package com.proofpoint.testing;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

//This is a copy from galaxy-server's com.proofpoint.galaxy.shared.FileUtils
public class FileUtils
{
    private static final int TEMP_DIR_ATTEMPTS = 10000;

    public static boolean isSymbolicLink(File file)
    {
        try {
            File canonicalFile = file.getCanonicalFile();
            File absoluteFile = file.getAbsoluteFile();
            // a symbolic link has a different name between the canonical and absolute path
            return !canonicalFile.getName().equals(absoluteFile.getName()) ||
                    // or the canonical parent path is not the same as the files parent path
                    !canonicalFile.getParent().equals(absoluteFile.getParentFile().getCanonicalPath());
        }
        catch (IOException e) {
            // error on the side of caution
            return true;
        }
    }

    public static ImmutableList<File> listFiles(File dir)
    {
        File[] files = dir.listFiles();
        if (files == null) {
            return ImmutableList.of();
        }
        return ImmutableList.copyOf(files);
    }

    public static ImmutableList<File> listFiles(File dir, FilenameFilter filter)
    {
        File[] files = dir.listFiles(filter);
        if (files == null) {
            return ImmutableList.of();
        }
        return ImmutableList.copyOf(files);
    }

    public static File createTempDir(String prefix)
    {
        return createTempDir(new File(System.getProperty("java.io.tmpdir")), prefix);
    }

    public static File createTempDir(File parentDir, String prefix)
    {
        String baseName = "";
        if (prefix != null) {
            baseName += prefix + "-";
        }

        baseName += System.currentTimeMillis() + "-";
        for (int counter = 0; counter < TEMP_DIR_ATTEMPTS; counter++) {
            File tempDir = new File(parentDir, baseName + counter);
            if (tempDir.mkdir()) {
                return tempDir;
            }
        }
        throw new IllegalStateException("Failed to create directory within "
                + TEMP_DIR_ATTEMPTS + " attempts (tried "
                + baseName + "0 to " + baseName + (TEMP_DIR_ATTEMPTS - 1) + ')');
    }

    public static boolean deleteDirectoryContents(File directory)
    {
        Preconditions.checkArgument(directory.isDirectory(), "Not a directory: %s", directory);

        // Don't delete symbolic link directories
        if (isSymbolicLink(directory)) {
            return false;
        }

        boolean success = true;
        for (File file : listFiles(directory)) {
            success = deleteRecursively(file) && success;
        }
        return success;
    }

    public static boolean deleteRecursively(File file)
    {
        boolean success = true;
        if (file.isDirectory()) {
            success = deleteDirectoryContents(file);
        }

        return file.delete() && success;
    }

    public static boolean copyDirectoryContents(File src, File target)
    {
        Preconditions.checkArgument(src.isDirectory(), "Source dir is not a directory: %s", src);

        // Don't delete symbolic link directories
        if (isSymbolicLink(src)) {
            return false;
        }

        target.mkdirs();
        Preconditions.checkArgument(target.isDirectory(), "Target dir is not a directory: %s", src);

        boolean success = true;
        for (File file : listFiles(src)) {
            success = copyRecursively(file, new File(target, file.getName())) && success;
        }
        return success;
    }

    public static boolean copyRecursively(File src, File target)
    {
        if (src.isDirectory()) {
            return copyDirectoryContents(src, target);
        }
        else {
            try {
                Files.copy(src, target);
                return true;
            }
            catch (IOException e) {
                return false;
            }
        }
    }

    public static File newFile(String parent, String... paths)
    {
        Preconditions.checkNotNull(parent, "parent is null");
        Preconditions.checkNotNull(paths, "paths is null");

        return newFile(new File(parent), ImmutableList.copyOf(paths));
    }

    public static File newFile(File parent, String... paths)
    {
        Preconditions.checkNotNull(parent, "parent is null");
        Preconditions.checkNotNull(paths, "paths is null");

        return newFile(parent, ImmutableList.copyOf(paths));
    }

    public static File newFile(File parent, Iterable<String> paths)
    {
        Preconditions.checkNotNull(parent, "parent is null");
        Preconditions.checkNotNull(paths, "paths is null");

        File result = parent;
        for (String path : paths) {
            result = new File(result, path);
        }
        return result;
    }
}