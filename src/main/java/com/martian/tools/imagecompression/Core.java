package com.martian.tools.imagecompression;

import com.google.common.base.Strings;
import com.google.common.io.Files;
import com.tinify.Source;
import com.tinify.Tinify;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Shenluw
 *         创建日期：2016/12/2 15:21
 */
public class Core {
    private Message message;
    private final Set<FileMap> fileMaps = new HashSet<>();
    private ExecutorService executorService;
    private final AtomicInteger success = new AtomicInteger();
    private final AtomicInteger count = new AtomicInteger();
    private final AtomicReference<StringBuilder> error = new AtomicReference<>(new StringBuilder());
    private final Object sync = new Object();
    private int total;

    public Core(Message message) {
        this.message = message;
        executorService = Executors.newFixedThreadPool(10);
    }

    public void compression(Options options) {
        Tinify.setKey(options.key);
        message.send("start -------------------------", null);

        List<String> froms = options.froms;
        fileMaps.clear();
        for (String from : froms) {
            if (Strings.isNullOrEmpty(from)) message.send("from dir is empty", null);
            else {
                File root = new File(from);
                if (assetFile(root)) {
                    extractDir(root, options.filter);
                }
            }
        }
        total = fileMaps.size();
        message.send("compression file count: " + fileMaps.size(), null);
        for (FileMap fileMap : fileMaps) {
            if (assetFile(fileMap.file)) {
                String dir = fileMap.dir;
                for (String p : froms) {
                    File file = new File(p);
                    String root = file.getParent();
                    if (dir.startsWith(root)) {
                        dir = dir.replace(root, "");
                    }
                }
                String toDir = options.to + File.separator + dir;
                execute(fileMap, options, toDir);
            }
        }
    }

    // FIXME: 2017/1/4 导入文件压缩未实现
    public void compression(Options options, String include) {
        if (Strings.isNullOrEmpty(include)) {
            message.send("include file is empty", null);
            return;
        }
        File file = new File(include);
        if (!file.exists()) {
            message.send("include file is not found", null);
            return;
        }
        try {
            String string = Files.toString(file, Charset.defaultCharset());
            String trim = string.trim();
            if (Strings.isNullOrEmpty(trim)) {
                message.send(String.format("compression include file is empty"), null);
                return;
            }
            String[] files = trim.split(",");
            fileMaps.clear();
            for (int i = 0; i < files.length; i++) {
                File f = new File(files[i]);
                if (!f.exists()) {
                    message.send(String.format("compression include file not exists, path = %s ", files[i]), null);
                } else {
                    if (options.filter.accept(f)) {
                        FileMap fileMap = new FileMap();
                        fileMap.dir = f.getParent();
                        fileMap.file = f;
                        fileMap.name = f.getName();
                        execute(fileMap, options, options.to + File.separator + "include");
                    }
                }
            }
        } catch (IOException e) {
            message.send(String.format("compression include file error, msg = %s ", e.getMessage()), e);
        }
    }

    private void execute(FileMap fileMap, Options options, String toDir) {
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    File to = new File(toDir);
                    if (!to.exists()) {
                        if (!to.mkdirs()) {
                            message.send(String.format("dir create error: %s", to), null);
                        }
                    }
                    message.send(String.format("compression: %s", fileMap.file.getAbsolutePath()), null);
                    compression(fileMap.file.getAbsolutePath(), toDir + File.separator + fileMap.name);
                    message.send(String.format("success: %s", fileMap.file.getAbsolutePath()), null);
                    success.incrementAndGet();
                    compressionDone(count.incrementAndGet());

                } catch (Exception e) {
                    StringBuilder builder = error.get();
                    builder.append(fileMap.file.getAbsolutePath()).append(",");
                    error.set(builder);
                    message.send(String.format("compression error file = %s ,msg = %s", fileMap.file.getAbsolutePath(), e.getMessage()), e);
                    compressionDone(count.incrementAndGet());
                    synchronized (sync) {
                        try {
                            if (Strings.isNullOrEmpty(options.errorLog)) return;
                            File file = new File(options.errorLog);
                            if (file.exists() || !file.isDirectory()) return;
                            Files.append(fileMap.file.getAbsolutePath() + ",", file, Charset.defaultCharset());
                        } catch (IOException er) {
                            message.send(String.format("compression error append error log = %s ", fileMap.file.getAbsolutePath(), er.getMessage()), er);
                        }
                    }
                }
            }
        });
    }

    private void compressionDone(int count) {
        if (count >= total) {
            int sc = success.get();
            message.send(String.format("compression file success count: %d , error count: %d", sc, fileMaps.size() - sc), null);
            message.send("end -------------------------", null);
        }
    }


    protected void compression(String from, String to) throws IOException {
        Source source = Tinify.fromFile(from);
        source.toFile(to);
    }

    public static Set<File> extractDir(File file, FileFilter filter, Set<File> out) {
        if (file.isFile() && filter.accept(file)) {
            out.add(file);
        } else {
            File[] files = file.listFiles(filter);
            if (files != null)
                for (File f : files) {
                    extractDir(f, filter, out);
                }
        }
        return out;
    }

    private void extractDir(File file, FileFilter filter) {
        if (file.isFile() && filter.accept(file)) {
            FileMap map = new FileMap();
            map.dir = file.getParent();
            map.name = file.getName();
            map.file = file;
            fileMaps.add(map);
        } else {
            File[] files = file.listFiles(filter);
            if (files == null) return;
            for (File f : files) {
                extractDir(f, filter);
            }
        }
    }

    private boolean assetFile(File file) {
        if (!file.exists()) {
            message.send(String.format("%s not exists", file.getAbsolutePath()), null);
            return false;
        } else if (!file.canRead()) {
            message.send(String.format("%s can not read", file.getAbsolutePath()), null);
            return false;
        }
        return true;
    }

    public class FileMap {
        String dir;
        String name;
        File file;
    }

    public static class Options {
        String key;
        List<String> froms = new ArrayList<String>();
        String to;
        String errorLog;
        FileFilter filter = new FileFilter() {
            String[] extension = {"png", "jpg", "jpeg"};

            @Override
            public boolean accept(File pathname) {
                if (pathname.isDirectory()) return true;
                for (String s : extension) {
                    if (pathname.getName().endsWith(s)) return true;
                }
                return false;
            }
        };
    }

    public interface Message {
        void send(String msg, Object obj);
    }
}
