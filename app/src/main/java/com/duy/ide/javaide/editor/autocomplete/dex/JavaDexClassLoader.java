package com.duy.ide.javaide.editor.autocomplete.dex;

import android.support.annotation.NonNull;

import com.duy.ide.javaide.editor.autocomplete.model.ClassDescription;
import com.duy.android.compiler.project.JavaProject;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by Duy on 20-Jul-17.
 */

public class JavaDexClassLoader {
    private static final String TAG = "JavaDexClassLoader";
    private JavaClassReader mClassReader;

    public JavaDexClassLoader(File classpath, File outDir) {
        mClassReader = new JavaClassReader(classpath.getPath(), outDir.getPath());
    }

    public JavaClassReader getClassReader() {
        return mClassReader;
    }

    @NonNull
    public ArrayList<ClassDescription> findClassWithPrefix(String simpleNamePrefix) {
        return mClassReader.findClass(simpleNamePrefix);
    }


    public void touchClass(String className) {
        ClassDescription classDescriptions = mClassReader.readClassByName(className, null);
    }

    public ClassDescription loadClass(String className) {
        return mClassReader.readClassByName(className, null);
    }

    public void loadAllClasses(JavaProject projectFile) {
        mClassReader.load(projectFile);
    }
}
