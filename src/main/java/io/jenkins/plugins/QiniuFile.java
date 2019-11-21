package io.jenkins.plugins;

import com.qiniu.common.QiniuException;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.model.FileInfo;
import com.qiniu.storage.model.FileListing;
import com.qiniu.util.Auth;
import jenkins.util.VirtualFile;
import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class QiniuFile extends VirtualFile {
    private static final Logger LOG = Logger.getLogger(QiniuFile.class.getName());

    private final String accessKey, secretKey, bucketName, objectName, downloadDomain;
    private final boolean useHTTPs;
    private boolean gotMetadata = false;
    private long fsize, lastModified;

    public QiniuFile(@Nonnull String accessKey, @Nonnull String secretKey, @Nonnull String bucketName,
                     @Nonnull String objectName, @Nonnull String downloadDomain, boolean useHTTPs) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.bucketName = bucketName;
        this.objectName = objectName;
        this.downloadDomain = downloadDomain;
        this.useHTTPs = useHTTPs;
    }

    @Nonnull
    public String getAccessKey() {
        return this.accessKey;
    }

    @Nonnull
    public String getSecretKey() {
        return this.secretKey;
    }

    @Nonnull
    public String getBucketName() {
        return this.bucketName;
    }

    @Nonnull
    public String getObjectName() {
        return this.objectName;
    }

    @Nonnull
    public String getDownloadDomain() {
        return this.downloadDomain;
    }

    public boolean isUseHTTPs() {
        return this.useHTTPs;
    }

    @Nonnull
    @Override
    public String getName() {
        LOG.log(Level.INFO, "QiniuFile::{0}::getName()", this.objectName);
        return new File(this.objectName).getName();
    }

    @Nonnull
    @Override
    public URI toURI() {
        LOG.log(Level.INFO, "QiniuFile::{0}::toURI()", this.objectName);
        try {
            return this.toExternalURL().toURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Nonnull
    @Override
    public URL toExternalURL() throws IOException {
        LOG.log(Level.INFO, "QiniuFile::{0}::toExternalURL()", this.objectName);
        String scheme = "http";
        if (this.useHTTPs) {
            scheme = "https";
        }

        String objectName = this.objectName;
        if (!objectName.startsWith("/")) {
            objectName = "/" + objectName;
        }

        final String url = new URL(scheme, this.downloadDomain, objectName).toString();
        return new URL(Auth.create(this.accessKey, this.secretKey).privateDownloadUrl(url));
    }

    @Override
    public VirtualFile getParent() {
        LOG.log(Level.INFO, "QiniuFile::{0}::getParent()", this.objectName);
        final String parentPath = new File(this.objectName).getParent() + File.separator;
        return new QiniuFile(this.accessKey, this.secretKey, this.bucketName, parentPath, this.downloadDomain, this.useHTTPs);
    }

    @Override
    public boolean isDirectory() throws IOException {
        LOG.log(Level.INFO, "QiniuFile::{0}::isDirectory()", this.objectName);
        return this.objectName.endsWith(File.separator);
    }

    @Override
    public boolean isFile() throws IOException {
        LOG.log(Level.INFO, "QiniuFile::{0}::isFile()", this.objectName);
        return !this.isDirectory();
    }

    @Override
    public boolean exists() throws IOException {
        LOG.log(Level.INFO, "QiniuFile::{0}::exists()", this.objectName);
        try {
            this.fetchMetadata();
            return true;
        } catch (QiniuException e) {
            return false;
        }
    }

    @Nonnull
    @Override
    public VirtualFile[] list() throws IOException {
        LOG.log(Level.INFO, "QiniuFile::{0}::list()", this.objectName);
        final BucketManager bucketManager = getBucketManager();
        final ArrayList<VirtualFile> listedFiles = new ArrayList<>();
        String marker = null;
        String prefix = this.objectName;
        if (!prefix.endsWith(File.separator)) {
            prefix += File.separator;
        }
        for (;;) {
            final FileListing list = bucketManager.listFiles(this.bucketName, prefix, marker, 1000, File.separator);
            if (list.items != null) {
                for (FileInfo file : list.items) {
                    if (!file.key.equals(prefix)) {
                        listedFiles.add(new QiniuFile(this.accessKey, this.secretKey, this.bucketName, file.key, this.downloadDomain, this.useHTTPs));
                    }
                }
            }
            if (list.commonPrefixes != null) {
                for (String commonPrefix : list.commonPrefixes) {
                    if (!commonPrefix.equals(prefix)) {
                        listedFiles.add(new QiniuFile(this.accessKey, this.secretKey, this.bucketName, commonPrefix, this.downloadDomain, this.useHTTPs));
                    }
                }
            }
            marker = list.marker;
            if (marker == null || marker.isEmpty()) {
                break;
            }
        }
        LOG.log(Level.INFO, "QiniuFile::{0}::list() done: {1} files listed", new Object[]{this.objectName, listedFiles.size()});
        VirtualFile[] virtualFiles = new VirtualFile[listedFiles.size()];
        return listedFiles.toArray(virtualFiles);
    }

    @Nonnull
    public VirtualFile[] listRecursively() throws IOException {
        LOG.log(Level.INFO, "QiniuFile::{0}::listRecursively()", this.objectName);
        final BucketManager bucketManager = getBucketManager();
        final ArrayList<VirtualFile> listedFiles = new ArrayList<>();
        String marker = null;
        String prefix = this.objectName;
        if (prefix.endsWith(File.separator)) {
            prefix += File.separator;
        }
        for (;;) {
            final FileListing list = bucketManager.listFiles(this.bucketName, prefix, marker, 1000, null);
            if (list.items != null) {
                for (FileInfo file : list.items) {
                    listedFiles.add(new QiniuFile(this.accessKey, this.secretKey, this.bucketName, file.key, this.downloadDomain, this.useHTTPs));
                }
            }
            marker = list.marker;
            if (marker == null || marker.isEmpty()) {
                break;
            }
        }
        LOG.log(Level.INFO, "QiniuFile::{0}::listRecursively() done: {1} files listed", new Object[]{this.objectName, listedFiles.size()});
        VirtualFile[] virtualFiles = new VirtualFile[listedFiles.size()];
        return listedFiles.toArray(virtualFiles);
    }

    public boolean deleteRecursively() throws IOException {
        LOG.log(Level.INFO, "QiniuFile::{0}::deleteRecursively()", this.objectName);
        final BucketManager bucketManager = getBucketManager();
        final VirtualFile[] files = this.listRecursively();
        final BucketManager.BatchOperations batch = new BucketManager.BatchOperations();

        if (files.length == 0) {
            return false;
        }

        int counter = 0;
        for (VirtualFile file : files) {
            final QiniuFile qiniuFile = (QiniuFile) file;
            batch.addDeleteOp(qiniuFile.bucketName, qiniuFile.objectName);
            counter++;
            if (counter >= 1000) {
                bucketManager.batch(batch);
                batch.clearOps();
            }
        }
        if (counter > 0) {
            bucketManager.batch(batch);
        }
        LOG.log(Level.INFO, "QiniuFile::{0}::deleteRecursively() done", this.objectName);
        return true;
    }

    @Nonnull
    @Override
    public VirtualFile child(@Nonnull String childName) {
        LOG.log(Level.INFO, "QiniuFile::{0}::child({1})", new Object[]{this.objectName, childName});
        return new QiniuFile(this.accessKey, this.secretKey, this.bucketName, new File(this.objectName, childName).getPath(), this.downloadDomain, this.useHTTPs);
    }

    @Override
    public long length() throws IOException {
        LOG.log(Level.INFO, "QiniuFile::{0}::length()", this.objectName);
        this.fetchMetadata();
        return this.fsize;
    }

    @Override
    public long lastModified() throws IOException {
        LOG.log(Level.INFO, "QiniuFile::{0}::lastModified()", this.objectName);
        this.fetchMetadata();
        return this.lastModified;
    }

    @Override
    public boolean canRead() throws IOException {
        LOG.log(Level.INFO, "QiniuFile::{0}::canRead()", this.objectName);
        return true;
    }

    @Nonnull
    @Override
    public InputStream open() throws IOException {
        LOG.log(Level.INFO, "QiniuFile::{0}::open()", this.objectName);
        return this.toExternalURL().openStream();
    }

    private void fetchMetadata() throws IOException {
        if (!this.gotMetadata && this.isFile()) {
            final FileInfo metadata = getBucketManager().stat(this.bucketName, this.objectName);
            this.gotMetadata = true;
            this.fsize = metadata.fsize;
            this.lastModified = metadata.putTime / 10000;
        }
    }

    @Nonnull
    private BucketManager getBucketManager() {
        return new BucketManager(this.getAuth(), this.getConfiguration());
    }

    @Nonnull
    private Auth getAuth() {
        return Auth.create(this.accessKey, this.secretKey);
    }

    @Nonnull
    private Configuration getConfiguration() {
        final Configuration config = new Configuration();
        config.useHttpsDomains = this.useHTTPs;
        return config;
    }
}
