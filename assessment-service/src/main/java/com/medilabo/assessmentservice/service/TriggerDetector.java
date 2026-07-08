package com.medilabo.assessmentservice.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Détecte les déclencheurs distincts présents dans les notes d'un patient.
 * Normalisation (minuscules + suppression des accents) appliquée au texte des notes
 * ET à la table des termes recherchés, matching en contains.
 */
@Component
public class TriggerDetector {

    private static final Map<String, List<String>> TRIGGERS = new LinkedHashMap<>();

    static {
        TRIGGERS.put("Hémoglobine A1C", List.of("hemoglobine a1c", "hba1c"));
        TRIGGERS.put("Microalbumine", List.of("microalbumine"));
        TRIGGERS.put("Taille", List.of("taille"));
        TRIGGERS.put("Poids", List.of("poids"));
        TRIGGERS.put("Fumeur", List.of("fumeur", "fumeuse", "fume"));
        TRIGGERS.put("Anormal", List.of("anormal"));
        TRIGGERS.put("Cholestérol", List.of("cholesterol"));
        TRIGGERS.put("Vertiges", List.of("vertige"));
        TRIGGERS.put("Rechute", List.of("rechute"));
        TRIGGERS.put("Réaction", List.of("reaction"));
        TRIGGERS.put("Anticorps", List.of("anticorps"));
    }

    /**
     * Un espace sépare les notes jointes pour éviter qu'un terme composé (ex: "hemoglobine a1c")
     * ne se forme artificiellement à la frontière entre la fin d'une note et le début de la suivante.
     */
    public List<String> detect(List<String> notes) {
        String normalizedText = notes.stream()
                .map(TriggerDetector::normalize)
                .collect(Collectors.joining(" "));

        List<String> found = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : TRIGGERS.entrySet()) {
            boolean matches = entry.getValue().stream().anyMatch(normalizedText::contains);
            if (matches) {
                found.add(entry.getKey());
            }
        }
        return found;
    }

    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String lower = text.toLowerCase(Locale.FRENCH);
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "");
    }
}