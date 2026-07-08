package com.medilabo.assessmentservice.service;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Textes des notes recopiés fidèlement depuis notes-service NoteSeeder (patients 1 à 4 de l'oracle OPC),
 * accents et apostrophes compris : une normalisation en amont fausserait le test de la vraie normalisation.
 */
class TriggerDetectorTest {

    private final TriggerDetector detector = new TriggerDetector();

    // --- Oracle : 4 patients seedés ---

    @Test
    void should_DetectOnlyPoids_When_Patient1Oracle() {
        List<String> notes = List.of(
                "Le patient déclare qu'il 'se sent très bien' Poids égal ou inférieur au poids recommandé"
        );

        List<String> found = detector.detect(notes);

        assertThat(found).containsExactly("Poids");
    }

    @Test
    void should_DetectAnormalAndReaction_When_Patient2Oracle() {
        List<String> notes = List.of(
                "Le patient déclare qu'il ressent beaucoup de stress au travail Il se plaint également que son audition est anormale dernièrement",
                "Le patient déclare avoir fait une réaction aux médicaments au cours des 3 derniers mois Il remarque également que son audition continue d'être anormale"
        );

        List<String> found = detector.detect(notes);

        assertThat(found).containsExactlyInAnyOrder("Anormal", "Réaction");
    }

    @Test
    void should_DetectFumeurAnormalCholesterol_When_Patient3Oracle() {
        List<String> notes = List.of(
                "Le patient déclare qu'il fume depuis peu",
                "Le patient déclare qu'il est fumeur et qu'il a cessé de fumer l'année dernière Il se plaint également de crises d'apnée respiratoire anormales Tests de laboratoire indiquant un taux de cholestérol LDL élevé"
        );

        List<String> found = detector.detect(notes);

        assertThat(found).containsExactlyInAnyOrder("Fumeur", "Anormal", "Cholestérol");
        assertThat(found).doesNotContain("Taille", "Poids");
    }

    @Test
    void should_DetectEightDistinctTriggers_When_Patient4Oracle() {
        List<String> notes = List.of(
                "Le patient déclare qu'il lui est devenu difficile de monter les escaliers Il se plaint également d'être essoufflé Tests de laboratoire indiquant que les anticorps sont élevés Réaction aux médicaments",
                "Le patient déclare qu'il a mal au dos lorsqu'il reste assis pendant longtemps",
                "Le patient déclare avoir commencé à fumer depuis peu Hémoglobine A1C supérieure au niveau recommandé",
                "Taille, Poids, Cholestérol, Vertige et Réaction"
        );

        List<String> found = detector.detect(notes);

        assertThat(found).hasSize(8);
        assertThat(found).containsExactlyInAnyOrder(
                "Hémoglobine A1C", "Taille", "Poids", "Fumeur", "Cholestérol", "Vertiges", "Réaction", "Anticorps"
        );
    }

    @Test
    void should_ProduceOracleRiskLevels_When_ChainingDetectionWithCalculation() {
        // Enchaîne detect() puis RiskCalculator.calculate() pour verrouiller le résultat final
        // de bout en bout sur les 4 patients de l'oracle (âge/genre : cf. docs/features/assessment.md).
        List<String> patient1Notes = List.of(
                "Le patient déclare qu'il 'se sent très bien' Poids égal ou inférieur au poids recommandé"
        );
        List<String> patient2Notes = List.of(
                "Le patient déclare qu'il ressent beaucoup de stress au travail Il se plaint également que son audition est anormale dernièrement",
                "Le patient déclare avoir fait une réaction aux médicaments au cours des 3 derniers mois Il remarque également que son audition continue d'être anormale"
        );
        List<String> patient3Notes = List.of(
                "Le patient déclare qu'il fume depuis peu",
                "Le patient déclare qu'il est fumeur et qu'il a cessé de fumer l'année dernière Il se plaint également de crises d'apnée respiratoire anormales Tests de laboratoire indiquant un taux de cholestérol LDL élevé"
        );
        List<String> patient4Notes = List.of(
                "Le patient déclare qu'il lui est devenu difficile de monter les escaliers Il se plaint également d'être essoufflé Tests de laboratoire indiquant que les anticorps sont élevés Réaction aux médicaments",
                "Le patient déclare qu'il a mal au dos lorsqu'il reste assis pendant longtemps",
                "Le patient déclare avoir commencé à fumer depuis peu Hémoglobine A1C supérieure au niveau recommandé",
                "Taille, Poids, Cholestérol, Vertige et Réaction"
        );

        assertThat(RiskCalculator.calculate(59, "F", detector.detect(patient1Notes).size()))
                .isEqualTo(RiskLevel.NONE);
        assertThat(RiskCalculator.calculate(80, "M", detector.detect(patient2Notes).size()))
                .isEqualTo(RiskLevel.BORDERLINE);
        assertThat(RiskCalculator.calculate(21, "M", detector.detect(patient3Notes).size()))
                .isEqualTo(RiskLevel.IN_DANGER);
        assertThat(RiskCalculator.calculate(23, "F", detector.detect(patient4Notes).size()))
                .isEqualTo(RiskLevel.EARLY_ONSET);
    }

    // --- Comptage distinct (une occurrence suffit, variantes = 1) ---

    @Test
    void should_CountFumeurOnce_When_FumeAndFumeurBothAppear() {
        List<String> found = detector.detect(List.of("Le patient fume et se déclare fumeur depuis 10 ans."));

        assertThat(found).containsExactly("Fumeur");
    }

    @Test
    void should_CountAnormalOnce_When_RepeatedAcrossMultipleNotes() {
        List<String> found = detector.detect(List.of(
                "Audition anormale.",
                "Toujours anormale au contrôle suivant.",
                "Anormale une troisième fois."
        ));

        assertThat(found).containsExactly("Anormal");
    }

    // --- Normalisation ---

    @Test
    void should_IgnoreCase_When_TriggerWordHasVariousCasing() {
        assertThat(detector.detect(List.of("ANORMAL"))).containsExactly("Anormal");
        assertThat(detector.detect(List.of("Anormal"))).containsExactly("Anormal");
        assertThat(detector.detect(List.of("anormal"))).containsExactly("Anormal");
    }

    @Test
    void should_IgnoreAccents_When_TriggerWordHasDiacritics() {
        assertThat(detector.detect(List.of("Cholestérol à surveiller."))).containsExactly("Cholestérol");
        assertThat(detector.detect(List.of("Hémoglobine A1C en hausse."))).containsExactly("Hémoglobine A1C");
    }

    @Test
    void should_MatchBothSingularAndPluralVertige_When_EitherFormIsPresent() {
        assertThat(detector.detect(List.of("Patient se plaint de vertige."))).containsExactly("Vertiges");
        assertThat(detector.detect(List.of("Patient se plaint de vertiges."))).containsExactly("Vertiges");
    }

    @Test
    void should_NotBreakDetection_When_TextContainsTypographicApostrophe() {
        // Apostrophe typographique U+2019, comme parfois produite par un copier-coller depuis un traitement de texte.
        List<String> found = detector.detect(List.of("Le patient n’a pas cessé de fumer et se plaint d’un poids en hausse"));

        assertThat(found).containsExactlyInAnyOrder("Fumeur", "Poids");
    }

    // --- Cas vides / négatifs ---

    @Test
    void should_ReturnEmptyList_When_NotesListIsEmpty() {
        assertThat(detector.detect(List.of())).isEmpty();
    }

    @Test
    void should_ReturnEmptyList_When_NoteContainsNoTrigger() {
        assertThat(detector.detect(List.of("Le patient va bien, aucune remarque particulière."))).isEmpty();
    }

    @Test
    void should_NotThrowAndIgnoreNullNote_When_NotesListContainsNull() {
        List<String> notes = Arrays.asList("Poids stable.", null);

        List<String> found = detector.detect(notes);

        assertThat(found).containsExactly("Poids");
    }

    // --- Ordre de sortie stable (ordre de la table des déclencheurs) ---

    @Test
    void should_ReturnTriggersInTableOrder_When_MultipleTriggersMatch() {
        List<String> found = detector.detect(List.of("Cholestérol et Taille anormale, avec Anticorps élevés."));

        assertThat(found).containsExactly("Taille", "Anormal", "Cholestérol", "Anticorps");
    }
}