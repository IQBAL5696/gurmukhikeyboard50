package com.iqbal.gurmukhikeyboard50;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class PredictionEngine {
    private List<String> dictionaryList = new ArrayList<>();
    private List<String> normalizedList = new ArrayList<>();
    private static final String TAG = "PredictionEngine";
    
    private static final String HALANT = "\u0A4D";
    private static final String HAHA = "\u0A39";
    private static final String KANNA = "\u0A3E";
    private static final String BIHARI = "\u0A40";

    public PredictionEngine(Context context) {
        loadDictionary(context);
    }

    private void loadDictionary(Context context) {
        try {
            FileInputStream fis = context.openFileInput("gurmukhi_dictionary.txt");
            BufferedReader br = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
            String line;
            List<String> tempWords = new ArrayList<>();
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    tempWords.add(line);
                }
            }
            br.close();
            
            Collections.reverse(tempWords);
            Set<String> uniqueWords = new LinkedHashSet<>(tempWords);
            dictionaryList = new ArrayList<>(uniqueWords);
            
            // Pre-calculate normalized versions for faster fuzzy matching
            normalizedList = new ArrayList<>(dictionaryList.size());
            for (String w : dictionaryList) {
                normalizedList.add(normalizeGurmukhi(w));
            }
            
            Log.d(TAG, "Loaded " + dictionaryList.size() + " words in priority order.");
        } catch (Exception e) {
            Log.e(TAG, "Error loading dictionary", e);
        }
    }

    public synchronized void updateDictionary(String word) {
        if (word == null || word.isEmpty()) return;
        int idx = dictionaryList.indexOf(word);
        if (idx != -1) {
            dictionaryList.remove(idx);
            normalizedList.remove(idx);
        }
        dictionaryList.add(0, word);
        normalizedList.add(0, normalizeGurmukhi(word));
    }

    public synchronized void removeWord(String word) {
        if (word == null || word.isEmpty()) return;
        int idx = dictionaryList.indexOf(word);
        if (idx != -1) {
            dictionaryList.remove(idx);
            normalizedList.remove(idx);
        }
    }

    public synchronized boolean isWordInDictionary(String word) {
        if (word == null) return false;
        return dictionaryList.contains(word);
    }

    public List<String> getSuggestions(String typed) {
        List<String> results = new ArrayList<>();
        if (typed == null || typed.isEmpty()) return results;

        synchronized (this) {
            // 1. Matra/Halant logic (Stays as it handles active typing structure)
            if (typed.endsWith(HALANT)) {
                results.add(typed + HAHA);
                results.add(typed + HALANT + KANNA);
                results.add(typed + HAHA + BIHARI);
            } else if (typed.endsWith(HALANT + HAHA)) {
                results.add(typed);
                results.add(typed + KANNA);
                results.add(typed + BIHARI);
            }

            // 2. Exact Prefix Matches (Priority) - These come from dictionaryList (paragraphs)
            for (String w : dictionaryList) {
                if (w.length() >= 2 && w.startsWith(typed)) {
                    if (!results.contains(w)) {
                        results.add(w);
                    }
                    if (results.size() >= 10) break;
                }
            }

            // 3. Fuzzy Matches (Using pre-calculated normalized list)
            if (results.size() < 5) {
                String normalizedTyped = normalizeGurmukhi(typed);
                if (!normalizedTyped.isEmpty()) {
                    for (int i = 0; i < dictionaryList.size(); i++) {
                        String normW = normalizedList.get(i);
                        if (normW.startsWith(normalizedTyped)) {
                            String original = dictionaryList.get(i);
                            if (!results.contains(original)) {
                                results.add(original);
                            }
                        }
                        if (results.size() >= 15) break;
                    }
                }
            }
        }
        return results;
    }

    private String normalizeGurmukhi(String word) {
        if (word == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            // Gurmukhi Vowels (0A3E to 0A4C) and other symbols
            if (c >= '\u0A3E' && c <= '\u0A4C') continue;
            if (c == '\u0A4D') continue; // Halant
            if (c >= '\u0A01' && c <= '\u0A03') continue; // Adak Bindi etc
            if (c == '\u0A70' || c == '\u0A71') continue; // Tippi/Addak
            sb.append(c);
        }
        return sb.toString();
    }

    public List<String> getGestureSuggestions(List<String> sequence) {
        List<String> results = new ArrayList<>();
        if (sequence == null || sequence.size() < 2) {
            Log.d(TAG, "Sequence too short: " + (sequence == null ? "null" : sequence.size()));
            return results;
        }

        String first = sequence.get(0).toLowerCase();
        String last = sequence.get(sequence.size() - 1).toLowerCase();
        Log.d(TAG, "Gesture search: first=" + first + ", last=" + last + ", path=" + sequence);

        synchronized (this) {
            if (dictionaryList.isEmpty()) {
                Log.w(TAG, "Dictionary is empty!");
            }
            for (String w : dictionaryList) {
                if (w.isEmpty()) continue;
                String lowW = w.toLowerCase();
                
                // Relaxed start/end check: Word starts with first character of path 
                // and ends with last character of path.
                if (lowW.startsWith(first) && lowW.endsWith(last)) {
                    if (isSequenceMatch(lowW, sequence)) {
                        if (!results.contains(w)) {
                            results.add(w);
                        }
                    }
                }
                if (results.size() >= 10) break;
            }
        }
        Log.d(TAG, "Gesture results found: " + results.size());
        return results;
    }

    private boolean isSequenceMatch(String word, List<String> sequence) {
        int seqIdx = 0;
        for (int i = 0; i < word.length(); i++) {
            char c = Character.toLowerCase(word.charAt(i));
            boolean found = false;
            // Search forward in the sequence for the current character of the word
            while (seqIdx < sequence.size()) {
                String s = sequence.get(seqIdx).toLowerCase();
                if (s.contains(String.valueOf(c))) {
                    found = true;
                    // Note: We don't increment seqIdx here, because the next char 
                    // in the word might also match this same key (e.g., 'll' in 'hello')
                    break;
                }
                seqIdx++;
            }
            if (!found) return false;
        }
        return true;
    }
}
