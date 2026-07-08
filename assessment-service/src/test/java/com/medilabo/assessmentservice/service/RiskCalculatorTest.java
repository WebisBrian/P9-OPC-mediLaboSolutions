package com.medilabo.assessmentservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RiskCalculatorTest {

    private static final int UNDER_30 = 25;
    private static final int OVER_30 = 40;

    // --- Table de vérité : patients < 30 ans ---

    @ParameterizedTest(name = "gender={0}, triggerCount={1} -> {2}")
    @CsvSource({
            "M, 0, NONE",
            "M, 2, NONE",
            "M, 3, IN_DANGER",
            "M, 4, IN_DANGER",
            "M, 5, EARLY_ONSET",
            "M, 6, EARLY_ONSET",
            "F, 0, NONE",
            "F, 3, NONE",
            "F, 4, IN_DANGER",
            "F, 5, IN_DANGER",
            "F, 6, IN_DANGER",
            "F, 7, EARLY_ONSET",
            "F, 8, EARLY_ONSET"
    })
    void should_MatchTruthTable_When_PatientIsUnder30(String gender, int triggerCount, RiskLevel expected) {
        assertThat(RiskCalculator.calculate(UNDER_30, gender, triggerCount)).isEqualTo(expected);
    }

    // --- Table de vérité : patients >= 30 ans (le genre n'intervient pas) ---

    @ParameterizedTest(name = "male, triggerCount={0} -> {1}")
    @CsvSource({
            "0, NONE",
            "1, NONE",
            "2, BORDERLINE",
            "5, BORDERLINE",
            "6, IN_DANGER",
            "7, IN_DANGER",
            "8, EARLY_ONSET",
            "10, EARLY_ONSET"
    })
    void should_MatchTruthTable_When_PatientIsOver30AndMale(int triggerCount, RiskLevel expected) {
        assertThat(RiskCalculator.calculate(OVER_30, "M", triggerCount)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "female, triggerCount={0} -> {1}")
    @CsvSource({
            "0, NONE",
            "1, NONE",
            "2, BORDERLINE",
            "5, BORDERLINE",
            "6, IN_DANGER",
            "7, IN_DANGER",
            "8, EARLY_ONSET",
            "10, EARLY_ONSET"
    })
    void should_MatchTruthTable_When_PatientIsOver30AndFemale(int triggerCount, RiskLevel expected) {
        assertThat(RiskCalculator.calculate(OVER_30, "F", triggerCount)).isEqualTo(expected);
    }

    // --- Frontière d'âge : "plus de 30 ans" interprété >= 30 ---

    @Test
    void should_ReturnBorderline_When_AgeExactly30WithTwoTriggers() {
        assertThat(RiskCalculator.calculate(30, "M", 2)).isEqualTo(RiskLevel.BORDERLINE);
        assertThat(RiskCalculator.calculate(30, "F", 2)).isEqualTo(RiskLevel.BORDERLINE);
    }

    @Test
    void should_SwitchFromNoneToBorderline_When_AgeCrosses30WithTwoTriggers() {
        // Documente la bascule de frontière : 29 ans reste sous le régime "<30" (pas de Borderline
        // sous 30 ans), 30 ans pile applique déjà la règle ">= 30".
        assertThat(RiskCalculator.calculate(29, "M", 2)).isEqualTo(RiskLevel.NONE);
        assertThat(RiskCalculator.calculate(30, "M", 2)).isEqualTo(RiskLevel.BORDERLINE);
    }

    // --- Cas contre-intuitifs (décisions assumées, pas des bugs) ---

    @Test
    void should_ReturnNone_When_Under30WithTwoTriggers_BorderlineIsOver30Only() {
        assertThat(RiskCalculator.calculate(UNDER_30, "M", 2)).isEqualTo(RiskLevel.NONE);
    }

    @Test
    void should_NeverDecreaseRisk_When_TriggerCountIncreases_ForMaleUnder30() {
        assertThat(RiskCalculator.calculate(UNDER_30, "M", 3)).isEqualTo(RiskLevel.IN_DANGER);
        assertThat(RiskCalculator.calculate(UNDER_30, "M", 4)).isEqualTo(RiskLevel.IN_DANGER);
        assertThat(RiskCalculator.calculate(UNDER_30, "M", 5)).isEqualTo(RiskLevel.EARLY_ONSET);
    }

    @Test
    void should_NeverDecreaseRisk_When_TriggerCountIncreases_ForFemaleUnder30() {
        assertThat(RiskCalculator.calculate(UNDER_30, "F", 4)).isEqualTo(RiskLevel.IN_DANGER);
        assertThat(RiskCalculator.calculate(UNDER_30, "F", 5)).isEqualTo(RiskLevel.IN_DANGER);
        assertThat(RiskCalculator.calculate(UNDER_30, "F", 6)).isEqualTo(RiskLevel.IN_DANGER);
        assertThat(RiskCalculator.calculate(UNDER_30, "F", 7)).isEqualTo(RiskLevel.EARLY_ONSET);
    }

    // --- Insensibilité à la casse du genre ---

    @Test
    void should_TreatGenderCaseInsensitively_When_CalculatingRisk() {
        assertThat(RiskCalculator.calculate(UNDER_30, "m", 3)).isEqualTo(RiskCalculator.calculate(UNDER_30, "M", 3));
        assertThat(RiskCalculator.calculate(UNDER_30, "f", 4)).isEqualTo(RiskCalculator.calculate(UNDER_30, "F", 4));
    }

    // --- Oracle : 4 patients seedés (docs/features/assessment.md) ---

    @Test
    void should_ReturnNone_When_Patient1Oracle() {
        assertThat(RiskCalculator.calculate(59, "F", 1)).isEqualTo(RiskLevel.NONE);
    }

    @Test
    void should_ReturnBorderline_When_Patient2Oracle() {
        assertThat(RiskCalculator.calculate(80, "M", 2)).isEqualTo(RiskLevel.BORDERLINE);
    }

    @Test
    void should_ReturnInDanger_When_Patient3Oracle() {
        assertThat(RiskCalculator.calculate(21, "M", 3)).isEqualTo(RiskLevel.IN_DANGER);
    }

    @Test
    void should_ReturnEarlyOnset_When_Patient4Oracle() {
        assertThat(RiskCalculator.calculate(23, "F", 8)).isEqualTo(RiskLevel.EARLY_ONSET);
    }
}