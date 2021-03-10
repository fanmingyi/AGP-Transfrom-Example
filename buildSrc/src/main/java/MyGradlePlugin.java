import com.android.build.gradle.AppPlugin;
import com.android.build.gradle.BaseExtension;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.model.ObjectFactory;

import javax.inject.Inject;

public abstract class MyGradlePlugin implements Plugin<Project> {

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    @Override
    public void apply(Project project) {

        FileSystemOperations fileSystemOperations = getFileSystemOperations();

//        MyTransform myTransform = new MyTransform(project);
//
//        plugin.getExtension().registerTransform(myTransform);
        project.getExtensions().findByType(BaseExtension.class)
                .registerTransform(new MyTransform(project,fileSystemOperations));
//        System.out.printf("");
    }
}
