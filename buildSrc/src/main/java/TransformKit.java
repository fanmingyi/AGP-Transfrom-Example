import com.android.build.gradle.BaseExtension;

import org.gradle.api.GradleException;
import org.gradle.api.Project;

import java.io.File;
import java.io.IOException;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;

public class TransformKit {

    ClassPool pool = ClassPool.getDefault();
Project project;

    public TransformKit(Project project) {
        this.project = project;
    }


    public void appendClassPath(String filePath) throws NotFoundException {
        pool.appendClassPath(filePath);
    }

    public void transform(String searchDir, String writeDir) throws NotFoundException, CannotCompileException, IOException {


        pool.appendClassPath(searchDir);
        CtClass mainCtClass = pool.get("com.example.agptramsform.MainActivity");

        CtClass baseProxyCtClass = pool.get("com.example.agptramsform.BaseProxyActivity");
        mainCtClass.setSuperclass(baseProxyCtClass);

        BaseExtension android = project.getExtensions().findByType(BaseExtension.class);
        pool.appendClassPath(android.getBootClasspath().get(0).toString());
        pool.importPackage("android.os.Bundle");

        CtMethod onCreate = mainCtClass.getDeclaredMethod("onCreate");
        onCreate.insertBefore("long _startTime = System.currentTimeMillis();");
        onCreate.insertAfter("long _endTime = System.currentTimeMillis();");

        mainCtClass.writeFile(searchDir);
        mainCtClass.detach();
        baseProxyCtClass.detach();
    }


}
