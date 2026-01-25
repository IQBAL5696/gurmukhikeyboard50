package com.iqbal;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class PredictionEngine {
    private List<String> dictionary = new ArrayList<>();
    private static final String TAG = "PredictionEngine"; // Added for logging

    public PredictionEngine(Context context) {
        // The dictionary is now loaded from internal storage,
        // where DictionaryHelper.convertParagraphToDictionary saves it.
        try {
            FileInputStream fis = context.openFileInput("gurmukhi_dictionary.txt");
            BufferedReader br = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
            String line;
            int wordCount = 0;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) { // Ignore empty lines and comments
                    dictionary.add(line);
                    wordCount++;
                }
            }
            br.close();
            Log.d(TAG, "Successfully loaded " + wordCount + " words from gurmukhi_dictionary.txt (internal storage).");
        } catch (FileNotFoundException e) {
            Log.e(TAG, "ERROR: gurmukhi_dictionary.txt not found in internal storage. Did DictionaryHelper run?");
            e.printStackTrace();
        } catch (IOException e) { // Catch other IOExceptions
            Log.e(TAG, "ERROR: IOException while reading gurmukhi_dictionary.txt from internal storage.");
            e.printStackTrace();
        } catch (Exception e) { // Catch any other unexpected exceptions
            Log.e(TAG, "ERROR: Unexpected exception while loading dictionary from internal storage.");
            e.printStackTrace();
        }
    }

    // Suggestions function
    public List<String> getSuggestions(String typed) {
        List<String> results = new ArrayList<>();
        if (typed == null || typed.isEmpty()) return results;

        String lowercasedTyped = typed.toLowerCase(); // Optional: for case-insensitive matching

        for (String w : dictionary) {
            // Optional: for case-insensitive matching, use w.toLowerCase().startsWith(lowercasedTyped)
            if (w.startsWith(typed)) { // Current: case-sensitive matching
                results.add(w);
                if (results.size() >= 5) break; // Max 5 suggestions
            }
        }
        return results;
    }
}
