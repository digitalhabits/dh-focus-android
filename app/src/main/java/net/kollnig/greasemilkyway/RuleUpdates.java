package net.kollnig.greasemilkyway;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

import net.kollnig.distractionlib.FilterRuleParser;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Downloads the built-in blocking rules from the project's repository once a day, so a rule
 * fixed there reaches users without waiting for a release.
 *
 * <p>Downloaded rules only add to the bundled ones ({@link ServiceConfig#getRules}). Navigation
 * rules are never downloaded: a wrong one taps things rather than failing visibly.
 */
final class RuleUpdates {
    private static final String TAG = "RuleUpdates";
    static final String STORED_FILE = "downloaded_rules.txt";
    private static final String ALLOWED_HOST = "raw.githubusercontent.com";
    private static final int MAX_BYTES = 256 * 1024;
    private static final int TIMEOUT_MS = 15000;
    private static final int JOB_ID = 1;
    private static final long INTERVAL_MS = 24L * 60 * 60 * 1000;

    private RuleUpdates() { }

    /** Schedules or cancels the daily download to match the user's setting. Idempotent. */
    static void sync(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null) {
            return;
        }
        if (!new ServiceConfig(context).isRuleUpdatesEnabled()) {
            scheduler.cancel(JOB_ID);
            storedFile(context).delete();
            return;
        }
        if (scheduler.getPendingJob(JOB_ID) != null) {
            return;
        }
        scheduler.schedule(new JobInfo.Builder(JOB_ID,
                new ComponentName(context, RuleUpdateJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(INTERVAL_MS)
                .build());
    }

    /**
     * Downloads and stores the rules. Blocks, so never call it on the main thread. On any
     * failure the previously stored rules are kept.
     *
     * @return whether new rules were stored
     */
    static boolean update(Context context) {
        String body;
        try {
            body = download(new URL(context.getString(R.string.rule_updates_url)));
        } catch (IOException e) {
            Log.w(TAG, "Rule download failed", e);
            return false;
        }

        List<String> lines = supportedLines(Arrays.asList(body.split("\n")));
        if (new FilterRuleParser().parseRules(lines.toArray(new String[0])).isEmpty()) {
            Log.w(TAG, "Downloaded rules contained no usable rule");
            return false;
        }
        return store(context, lines);
    }

    /** The stored rule lines, or none if updates have never succeeded. */
    static List<String> readStored(Context context) {
        File file = storedFile(context);
        if (!file.exists()) {
            return Collections.emptyList();
        }
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            Log.w(TAG, "Could not read stored rules", e);
            return Collections.emptyList();
        }
        return supportedLines(lines);
    }

    /**
     * Drops blank lines, comment lines and rules using keys this version does not understand.
     * Comment lines go too because the parser attaches one to the next rule, which may have
     * been dropped.
     */
    static List<String> supportedLines(List<String> lines) {
        List<String> supported = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (FilterRuleParser.usesOnlyKnownKeys(trimmed)) {
                supported.add(trimmed);
            }
        }
        return supported;
    }

    private static String download(URL url) throws IOException {
        if (!"https".equals(url.getProtocol()) || !ALLOWED_HOST.equals(url.getHost())) {
            throw new IOException("Refusing to download rules from " + url);
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            // A redirect could lead anywhere, which would void the host check above.
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setUseCaches(false);
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + status);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = connection.getInputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    if (out.size() > MAX_BYTES) {
                        throw new IOException("Rules file larger than " + MAX_BYTES + " bytes");
                    }
                }
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    /** Writes to a temporary file and renames it, so a reader never sees half a file. */
    private static boolean store(Context context, List<String> lines) {
        File target = storedFile(context);
        File temp = new File(context.getFilesDir(), STORED_FILE + ".tmp");
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(temp),
                StandardCharsets.UTF_8)) {
            writer.write(String.join("\n", lines));
        } catch (IOException e) {
            Log.w(TAG, "Could not store downloaded rules", e);
            temp.delete();
            return false;
        }
        if (!temp.renameTo(target)) {
            temp.delete();
            return false;
        }
        return true;
    }

    private static File storedFile(Context context) {
        return new File(context.getFilesDir(), STORED_FILE);
    }
}
