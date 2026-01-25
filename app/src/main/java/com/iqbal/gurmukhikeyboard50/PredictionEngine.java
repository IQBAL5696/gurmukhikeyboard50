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
    private static final String TAG = "PredictionEngine";

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
            
            // Reverse the list so that newly learned words (added at the end of the file)
            // appear at the beginning of our search.
            Collections.reverse(tempWords);
            
            // Use LinkedHashSet to keep them unique while maintaining the new "reverse" order
            Set<String> uniqueWords = new LinkedHashSet<>(tempWords);
            dictionaryList = new ArrayList<>(uniqueWords);
            
            Log.d(TAG, "Loaded " + dictionaryList.size() + " words in priority order.");
        } catch (Exception e) {
            Log.e(TAG, "Error loading dictionary", e);
        }
    }

    public List<String> getSuggestions(String typed) {
        List<String> results = new ArrayList<>();
        if (typed == null || typed.isEmpty()) return results;

        // Search through the prioritized list
        for (String w : dictionaryList) {
            if (w.startsWith(typed)) {
                results.add(w);
                if (results.size() >= 20) break; // Increased limit for scrollable bar
            }
        }
        return results;
    }
}
