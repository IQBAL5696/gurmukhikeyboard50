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

    public synchronized boolean isWordInDictionary(String word) {
        if (word == null) return false;
        return dictionaryList.contains(word);
    }

    public List<String> getSuggestions(String typed) {
        List<String> results = new ArrayList<>();
        if (typed == null || typed.isEmpty()) return results;

        synchronized (this) {
            // 1. Single Character Triggers
            if (typed.length() == 1) {
                handleSingleCharTriggers(typed, results);
            } else if (typed.endsWith(HALANT)) {
                results.add(typed + HAHA);
                results.add(typed + HALANT + KANNA);
                results.add(typed + HAHA + BIHARI);
            } else if (typed.endsWith(HALANT + HAHA)) {
                results.add(typed);
                results.add(typed + KANNA);
                results.add(typed + BIHARI);
            }

            // 2. Exact Prefix Matches (Priority)
            for (String w : dictionaryList) {
                if (w.startsWith(typed)) {
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

    private void handleSingleCharTriggers(String typed, List<String> results) {
        switch (typed) {
            case "ਮ":
                results.add("ਮੈਂ");
                results.add("ਮੈਨੂੰ");
                results.add("ਮੇਰਾ");
                results.add("ਮੇਰੇ");
                results.add("ਮੇਰੀ");
                break;
            case "ੳ":
                results.add("ਉਹ");
                results.add("ਉਸ");
                results.add("ਉੱਤੇ");
                results.add("ਉਦਾਹਰਨ");
                results.add("ਉਸਾਰੀ");
                results.add("ਉਡੀਕ");
                results.add("ਉਤਸ਼ਾਹ");
                break;
            case "ੲ":
                results.add("ਇਹ");
                results.add("ਇਸ");
                results.add("ਇੱਕ");
                results.add("ਇੱਥੇ");
                results.add("ਇਵੇਂ");
                results.add("ਇਮਾਨਦਾਰੀ");
                break;
            case "ਸ":
                results.add("ਸ੍ਰੀ");
                results.add("ਸਤਿ");
                results.add("ਸਾਡਾ");
                results.add("ਸਭ");
                results.add("ਸਕੂਲ");
                break;
            case "ਹ":
                results.add("ਹੋਰ");
                results.add("ਹਾਲ");
                results.add("ਹਾਲੇ");
                results.add("ਹਮੇਸ਼ਾ");
                break;
            case "ਗ":
                results.add("ਗੁਰੂ");
                results.add("ਗਏ");
                results.add("ਗੱਲ");
                results.add("ਗੱਡੀ");
                results.add("ਗੁਰਦੁਆਰੇ");
                break;
            case "ਚ":
                results.add("ਚਾਹ");
                results.add("ਚੱਲੋ");
                results.add("ਚੰਗਾ");
                results.add("ਚਾਹੀਦਾ");
                results.add("ਚਿੱਠੀ");
                break;
            case "ਦ":
                results.add("ਦੱਸੋ");
                results.add("ਦੇਸ਼");
                results.add("ਦੋਸਤ");
                results.add("ਦੁਕਾਨ");
                results.add("ਦਵਾਈ");
                break;
            case "ਨ":
                results.add("ਨਹੀਂ");
                results.add("ਨਾਮ");
                results.add("ਨਾਲ");
                results.add("ਨਵਾਂ");
                results.add("ਨੌਕਰੀ");
                break;
            case "ਬ":
                results.add("ਬਹੁਤ");
                results.add("ਬੱਚੇ");
                results.add("ਬੱਸ");
                results.add("ਬੋਲੋ");
                results.add("ਬਾਜ਼ਾਰ");
                break;
            case "ਲ":
                results.add("ਲਓ");
                results.add("ਲਿਆਓ");
                results.add("ਲਾਲ");
                results.add("ਲੰਮੀ");
                results.add("ਲੜਕੀ");
                break;
            case "ਵ":
                results.add("ਵਾਹਿਗੁਰੂ");
                results.add("ਵਧੀਆ");
                results.add("ਵਾਲਾ");
                results.add("ਵਿੱਚ");
                results.add("ਵਿਆਹ");
                break;
            case "ਰ":
                results.add("ਰੋਟੀ");
                results.add("ਰਸਤਾ");
                results.add("ਰੱਖੋ");
                results.add("ਰੰਗ");
                results.add("ਰਿਸ਼ਤਾ");
                break;
            case "ਖ":
                results.add("ਖਾਣਾ");
                results.add("ਖੁਸ਼");
                results.add("ਖੇਡ");
                results.add("ਖਤਮ");
                results.add("ਖੂਹ");
                break;
            case "ਫ":
                results.add("ਫਲ");
                results.add("ਫੁੱਲ");
                results.add("ਫੋਨ");
                results.add("ਫੜੋ");
                results.add("ਫਰਕ");
                break;
            case "ਭ":
                results.add("ਭਰਾ");
                results.add("ਭੈਣ");
                results.add("ਭਾਰਤ");
                results.add("ਭੁੱਲ");
                results.add("ਭੇਜੋ");
                break;
            case "ਧ":
                results.add("ਧੰਨਵਾਦ");
                results.add("ਧਿਆਨ");
                results.add("ਧੀ");
                results.add("ਧਰਮ");
                results.add("ਧੁੱਪ");
                break;
            case "ਟ":
                results.add("ਟਾਇਮ");
                results.add("ਟਿਕਟ");
                results.add("ਟੁੱਟ");
                break;
            case "ਯ":
                results.add("ਯਾਦ");
                results.add("ਯਕੀਨ");
                results.add("ਯੋਜਨਾ");
                break;
            case "ਥ":
                results.add("ਥਾਂ");
                results.add("ਥੋੜਾ");
                results.add("ਥੱਲੇ");
                break;
            case "ਅ":
                results.add("ਅੱਜ");
                results.add("ਅਸੀਂ");
                results.add("ਆਪਣਾ");
                break;
            case "ਉ":
                results.add("ਉਹ");
                results.add("ਉਸ");
                results.add("ਉਪਰ");
                break;
            case "ਕ":
                results.add("ਕੀ");
                results.add("ਕਿਉਂ");
                results.add("ਕਦੋਂ");
                results.add("ਕਿਵੇਂ");
                results.add("ਕੰਮ");
                results.add("ਕੋਈ");
                break;
            case "ਤ":
                results.add("ਤੁਸੀਂ");
                results.add("ਤੂੰ");
                results.add("ਤੁਹਾਡਾ");
                results.add("ਤੈਨੂੰ");
                results.add("ਤੇਰੇ");
                break;
            case "ਜ":
                results.add("ਜੀ");
                results.add("ਜਦੋਂ");
                results.add("ਜਲਦੀ");
                results.add("ਜਿੱਥੇ");
                results.add("ਜਿਵੇਂ");
                results.add("ਜੋ");
                break;
            case "ਘ":
                results.add("ਘਰ");
                results.add("ਘੜੀ");
                results.add("ਘੱਟ");
                results.add("ਘਰਵਾਲੇ");
                break;
            case "ਛ":
                results.add("ਛੋਟਾ");
                results.add("ਛੁੱਟੀ");
                results.add("ਛੇਤੀ");
                results.add("ਛੱਡੋ");
                break;
            case "ਝ":
                results.add("ਝੂਠ");
                results.add("ਝਟਕਾ");
                results.add("ਝੰਡਾ");
                break;
            case "ਠ":
                results.add("ਠੀਕ");
                results.add("ਠੰਢ");
                results.add("ਠਹਿਰੋ");
                break;
            case "ਡ":
                results.add("ਡਾਕਟਰ");
                results.add("ਡਰ");
                results.add("ਡੰਡਾ");
                results.add("ਡਿੱਗ");
                break;
            case "ਢ":
                results.add("ਢਿੱਲ");
                results.add("ਢੇਰ");
                results.add("ਢਾਹੁਣ");
                break;
            case "ਸ਼":
                results.add("ਸ਼ਹਿਰ");
                results.add("ਸ਼ਾਮ");
                results.add("ਸ਼ੁੱਕਰਵਾਰ");
                results.add("ਸ਼ਾਇਦ");
                break;
            case "ਜ਼":
                results.add("ਜ਼ਰੂਰ");
                results.add("ਜ਼ਮੀਨ");
                results.add("ਜ਼ਿੰਦਗੀ");
                break;
        }
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
                if (w.length() < 2) continue;
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
