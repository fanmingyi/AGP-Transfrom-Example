import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.google.common.collect.ImmutableSet;

import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import static com.android.build.gradle.internal.pipeline.TransformManager.CONTENT_CLASS;

public class MyTransform extends Transform {

    Project project;
    FileSystemOperations fileHelper;

    public MyTransform(Project project, FileSystemOperations fileSystemOperations) {
        this.project = project;
        fileHelper = fileSystemOperations;
    }

    Logger logger = Logging.getLogger(MyTransform.class);

    @Override
    public String getName() {
        return "MyFmyMyTransform";
    }

    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return CONTENT_CLASS;
    }

    @Override
    public Set<? super QualifiedContent.Scope> getScopes() {
        Set<QualifiedContent.ScopeType> d =
                ImmutableSet.of(QualifiedContent.Scope.PROJECT, QualifiedContent.Scope.SUB_PROJECTS, QualifiedContent.Scope.EXTERNAL_LIBRARIES);
        return d;
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    public void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        super.transform(transformInvocation);


        TransformKit transformKit = new TransformKit(project);

        Collection<TransformInput> inputs = transformInvocation.getInputs();

        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();


        //当前不是增量更新的那么删除这个transform的缓存文件 //build/intermediates/transforms/xxxxxx
        if (!transformInvocation.isIncremental()) {
            outputProvider.deleteAll();
        }

        logger.error("监测是否增量 " + transformInvocation.isIncremental());

        for (TransformInput transformInput : inputs) {

            //遍历jar文件 对jar不操作，但是要输出到out路径
            transformInput.getJarInputs().parallelStream().forEach(jarInput -> {
                File dst = outputProvider.getContentLocation(
                        jarInput.getName(), jarInput.getContentTypes(), jarInput.getScopes(),
                        Format.JAR);

                if (transformInvocation.isIncremental()) {
                    switch (jarInput.getStatus()) {
                        //未做任何改变
                        case NOTCHANGED:
                            break;
                        case ADDED:
                            //如果一个jar被添加，需要被拷贝回来
                        case CHANGED:
                            //是一个新的文件，那么需要拷贝回来
                            try {
                                FileUtils.copyFile(jarInput.getFile(), dst);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;
                        //当前的输入源已经被删除，那么transform下的对应文件理应被删除
                        case REMOVED:
                            if (jarInput.getFile().exists()) {
                                try {
                                    FileUtils.forceDelete(jarInput.getFile());
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                            break;
                    }
                } else {
                    //非增量拷贝源集类路径
                    logger.error("jar 输入状态 " + jarInput.getStatus());
                    try {
                        FileUtils.copyFile(jarInput.getFile(), dst);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });


            for (DirectoryInput directoryInput : transformInput.getDirectoryInputs()) {
                // 获取输出目录
                File dest = outputProvider.getContentLocation(directoryInput.getName(),
                        directoryInput.getContentTypes(), directoryInput.getScopes(), Format.DIRECTORY);


                FileCollection filter = project.fileTree(directoryInput.getFile())
                        .filter(innerFile -> innerFile.getName().equals("MainActivity.class"));

                //当前是增量的状态，所以遍历这个文件夹下的所有文件
                if (transformInvocation.isIncremental()) {

                    Map<File, Status> changedFiles = directoryInput.getChangedFiles();

                    //遍历文件状态
                    BiConsumer<File, Status> fileStatusBiConsumer = (file, status) -> {
                        switch (status) {
                            //这个文件夹不做任何事情
                            case NOTCHANGED:
                                break;
                            case CHANGED:
                            case ADDED:
                                //顺带检查下是否存在我们目标的文件，如果存在那么修改字节码后在拷贝
                                if (file.getName().equals("MainActivity.class")) {
                                    try {
                                        transformKit.transform(directoryInput.getFile().getAbsolutePath(), dest.getAbsolutePath());
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }

                                try {
                                    /**
                                     * 处理方式一 简单粗暴
                                     */
                                    //偷懒就直接拷贝文件夹 但是效率低
//                                    FileUtils.copyDirectory(directoryInput.getFile(), dest);


                                    /**
                                     * 处理方式二 高效 略复杂
                                     */
                                    //构建目录连带包名
                                    //file 可能的目录是 /build/intermediates/java/debug/com/fmy/MainActivity.class
                                    //dest 可能目标地址 /build/intermediates/transforms/mytrasnsfrom/debug/40/
                                    //directoryInput.getFile() 可能的输入类的文件夹  /build/intermediates/java/debug/
                                    File dirFile = directoryInput.getFile();

                                    String prefixPath = file.getAbsolutePath().replaceFirst(dirFile.getAbsolutePath(), "");
                                    System.out.println();

                                    //重新拼接成/build/intermediates/transforms/mytrasnsfrom/debug/40/com/fmy/MainActivity.class
                                    File specifyDest = new File(dest.getAbsolutePath(), prefixPath);
                                    FileUtils.copyFile(file, specifyDest);

                                    logger.error("触发增量 最终写入的目的路径"+specifyDest);

                                } catch (Exception e) {
                                    e.printStackTrace();
                                }

                                break;
                            case REMOVED:
                                //文件被删除直接删除相关文件即可
                                try {
                                    FileUtils.forceDelete(file);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                break;
                        }

                    };
                    changedFiles.forEach(fileStatusBiConsumer);
                } else {
                    if (!filter.isEmpty()) {

                        try {
                            transformKit.transform(directoryInput.getFile().getAbsolutePath(), dest.getAbsolutePath());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    FileUtils.copyDirectory(directoryInput.getFile(), dest);
                }


            }

        }

    }
}
