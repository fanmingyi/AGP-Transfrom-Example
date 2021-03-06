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


        //?????????????????????????????????????????????transform??????????????? //build/intermediates/transforms/xxxxxx
        if (!transformInvocation.isIncremental()) {
            outputProvider.deleteAll();
        }

        logger.error("?????????????????? " + transformInvocation.isIncremental());

        for (TransformInput transformInput : inputs) {

            //??????jar?????? ???jar??????????????????????????????out??????
            transformInput.getJarInputs().parallelStream().forEach(jarInput -> {
                File dst = outputProvider.getContentLocation(
                        jarInput.getName(), jarInput.getContentTypes(), jarInput.getScopes(),
                        Format.JAR);

                if (transformInvocation.isIncremental()) {
                    switch (jarInput.getStatus()) {
                        //??????????????????
                        case NOTCHANGED:
                            break;
                        case ADDED:
                            //????????????jar?????????????????????????????????
                        case CHANGED:
                            //????????????????????????????????????????????????
                            try {
                                FileUtils.copyFile(jarInput.getFile(), dst);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;
                        //??????????????????????????????????????????transform?????????????????????????????????
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
                    //??????????????????????????????
                    logger.error("jar ???????????? " + jarInput.getStatus());
                    try {
                        FileUtils.copyFile(jarInput.getFile(), dst);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });


            for (DirectoryInput directoryInput : transformInput.getDirectoryInputs()) {
                // ??????????????????
                File dest = outputProvider.getContentLocation(directoryInput.getName(),
                        directoryInput.getContentTypes(), directoryInput.getScopes(), Format.DIRECTORY);


                FileCollection filter = project.fileTree(directoryInput.getFile())
                        .filter(innerFile -> innerFile.getName().equals("MainActivity.class"));

                //????????????????????????????????????????????????????????????????????????
                if (transformInvocation.isIncremental()) {

                    Map<File, Status> changedFiles = directoryInput.getChangedFiles();

                    //??????????????????
                    BiConsumer<File, Status> fileStatusBiConsumer = (file, status) -> {
                        switch (status) {
                            //?????????????????????????????????
                            case NOTCHANGED:
                                break;
                            case CHANGED:
                            case ADDED:
                                //????????????????????????????????????????????????????????????????????????????????????????????????
                                if (file.getName().equals("MainActivity.class")) {
                                    try {
                                        transformKit.transform(directoryInput.getFile().getAbsolutePath(), dest.getAbsolutePath());
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }

                                try {
                                    /**
                                     * ??????????????? ????????????
                                     */
                                    //?????????????????????????????? ???????????????
//                                    FileUtils.copyDirectory(directoryInput.getFile(), dest);


                                    /**
                                     * ??????????????? ?????? ?????????
                                     */
                                    //????????????????????????
                                    //file ?????????????????? /build/intermediates/java/debug/com/fmy/MainActivity.class
                                    //dest ?????????????????? /build/intermediates/transforms/mytrasnsfrom/debug/40/
                                    //directoryInput.getFile() ??????????????????????????????  /build/intermediates/java/debug/
                                    File dirFile = directoryInput.getFile();

                                    String prefixPath = file.getAbsolutePath().replaceFirst(dirFile.getAbsolutePath(), "");
                                    System.out.println();

                                    //???????????????/build/intermediates/transforms/mytrasnsfrom/debug/40/com/fmy/MainActivity.class
                                    File specifyDest = new File(dest.getAbsolutePath(), prefixPath);
                                    FileUtils.copyFile(file, specifyDest);

                                    logger.error("???????????? ???????????????????????????"+specifyDest);

                                } catch (Exception e) {
                                    e.printStackTrace();
                                }

                                break;
                            case REMOVED:
                                //?????????????????????????????????????????????
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
