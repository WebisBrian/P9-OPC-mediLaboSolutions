package com.medilabo.assessmentservice.service;

/**
 * Table de décision du niveau de risque.
 * Fonction pure, sans état ni dépendance : pas de composant Spring, testable directement.
 * Ordre d'évaluation : Early onset > In Danger > Borderline > None (priorité au plus grave).
 */
final class RiskCalculator {

    private static final int AGE_THRESHOLD = 30;

    private RiskCalculator() {
    }

    static RiskLevel calculate(int age, String gender, int triggerCount) {
        boolean male = "M".equalsIgnoreCase(gender);

        if (age < AGE_THRESHOLD) {
            if (male && triggerCount >= 5) {
                return RiskLevel.EARLY_ONSET;
            }
            if (!male && triggerCount >= 7) {
                return RiskLevel.EARLY_ONSET;
            }
            if (male && triggerCount >= 3) {
                return RiskLevel.IN_DANGER;
            }
            if (!male && triggerCount >= 4) {
                return RiskLevel.IN_DANGER;
            }
            return RiskLevel.NONE;
        }

        if (triggerCount >= 8) {
            return RiskLevel.EARLY_ONSET;
        }
        if (triggerCount >= 6) {
            return RiskLevel.IN_DANGER;
        }
        if (triggerCount >= 2) {
            return RiskLevel.BORDERLINE;
        }
        return RiskLevel.NONE;
    }
}