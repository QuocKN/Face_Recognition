package com.atharvakale.facerecognition.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Pair;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.*;

public class FaceStorage {

    private final SharedPreferences sp;
    private final Gson gson = new Gson();

    public FaceStorage(Context ctx) {
        sp = ctx.getSharedPreferences("faces", Context.MODE_PRIVATE);
    }

    public void addFace(String name, float[] emb) {
        HashMap<String, List<float[]>> map = readRaw();
        map.computeIfAbsent(name, k -> new ArrayList<>()).add(emb);
        sp.edit().putString("map", gson.toJson(map)).apply();
    }

    public void clear() {
        sp.edit().clear().apply();
    }

    private HashMap<String, List<float[]>> readRaw() {
        String json = sp.getString("map", "{}");
        return gson.fromJson(json,
                new TypeToken<HashMap<String, List<float[]>>>(){}.getType());
    }

    public Pair<String, Float> findNearest(float[] emb) {
        float min = Float.MAX_VALUE;
        String best = "Unknown";

        for (Map.Entry<String, List<float[]>> e : readRaw().entrySet()) {
            for (float[] known : e.getValue()) {
                float d = 0;
                for (int i = 0; i < emb.length; i++) {
                    float diff = emb[i] - known[i];
                    d += diff * diff;
                }
                d = (float) Math.sqrt(d);
                if (d < min) {
                    min = d;
                    best = e.getKey();
                }
            }
        }
        return new Pair<>(best, min);
    }
}
