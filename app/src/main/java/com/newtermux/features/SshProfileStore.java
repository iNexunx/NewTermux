package com.newtermux.features;

import com.termux.shared.termux.TermuxConstants;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class SshProfileStore {

    private static final String FILE_NAME = ".termux/ssh-profiles.json";

    private static File profilesFile() {
        return new File(TermuxConstants.TERMUX_HOME_DIR_PATH, FILE_NAME);
    }

    public static List<SshProfile> load() {
        List<SshProfile> list = new ArrayList<>();
        File f = profilesFile();
        if (!f.exists()) return list;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            JSONArray arr = new JSONArray(sb.toString());
            for (int i = 0; i < arr.length(); i++) {
                list.add(SshProfile.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) {}
        return list;
    }

    public static void save(List<SshProfile> profiles) {
        try {
            File f = profilesFile();
            f.getParentFile().mkdirs();
            JSONArray arr = new JSONArray();
            for (SshProfile p : profiles) arr.put(p.toJson());
            try (FileWriter fw = new FileWriter(f)) {
                fw.write(arr.toString(2));
            }
        } catch (Exception ignored) {}
    }
}
