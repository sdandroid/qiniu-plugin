package io.jenkins.plugins;

import com.qiniu.storage.BucketManager;
import com.qiniu.storage.Configuration;
import com.qiniu.util.Auth;
import jenkins.util.VirtualFile;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

@Restricted(NoExternalUse.class)
public class QiniuFile extends VirtualFile {
    private static final Logger LOG = Logger.getLogger(QiniuFile.class.getName());

    private Path objectName;
    private QiniuFileSystem qiniuFileSystem;

    public QiniuFile(@Nonnull QiniuFileSystem qiniuFileSystem, Path objectName) {
        this.qiniuFileSystem = qiniuFileSystem;
        this.objectName = objectName;
    }

    @Nonnull
    public Path getPath() {
        String objectName = "";
        if (this.objectName != null) {
            objectName = this.objectName.toString();
        }
        Path path = this.qiniuFileSystem.getObjectNamePrefix();
        if (path != null) {
            return path.resolve(objectName);
        } else {
            return FileSystems.getDefault().getPath(objectName);
        }
    }

    @Nonnull
    @Override
    public String getName() {
        LOG.log(Level.INFO, "QiniuFile::{0}::getName()", this.objectName);
        final Path path = getPath().getFileName();
        if (path != null) {
            return path.toString();
        } else {
            return "";
        }
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
        if (this.qiniuFileSystem.getConfig().isUseHTTPs()) {
            scheme = "https";
        }

        String objectName = this.getPath().toString();
        if (!objectName.startsWith("/")) {
            objectName = "/" + objectName;
        }

        final String url = new URL(scheme, this.qiniuFileSystem.getConfig().getDownloadDomain(), objectName).toString();
        LOG.log(Level.INFO, "QiniuFile::{0}::toExternalURL() url={0}", url);
        return new URL(this.qiniuFileSystem.getConfig().getAuth().privateDownloadUrl(url));
    }

    @CheckForNull
    @Override
    public VirtualFile getParent() {
        LOG.log(Level.INFO, "QiniuFile::{0}::getParent()", this.objectName);
        if (this.objectName != null) {
            final Path parentPath = this.objectName.getParent();
            return new QiniuFile(this.qiniuFileSystem, parentPath);
        } else {
            return null;
        }
    }

    @Override
    public boolean isDirectory() throws IOException {
        LOG.log(Level.INFO, "QiniuFile::{0}::isDirectory()", this.objectName);
        this.qiniuFileSystem.mayThrowIOException();
        if (this.objectName != null) {
            final QiniuFileSystem.Node node = this.qiniuFileSystem.getNodeByPath(this.objectName, false, false);
            if (node != null) {
                return node.isDirectory();
            } else {
                return false;
            }
        } else {
            return true;
        }
    }

    @Override
    public boolean isFile() throws IOException {
        LOG.log(Level.INFO, "QiniuFile::{0}::isFile()", this.objectName);
        this.qiniuFileSystem.mayThrowIOException();
        if (this.objectName != null) {
            final QiniuFileSystem.Node node = this.qiniuFileSystem.getNodeByPath(this.objectName, false, false);
            if (node != null) {
                return node.isFile();
            } else {
                return false;
            }
        } else {
            return true;
        }
    }

    @Override
    public boolean exists() throws IOException {
        LOG.log(Level.INFO, "QiniuFile::{0}::exists()", this.objectName);
        this.qiniuFileSystem.mayThrowIOException();
        try {
            if (this.objectName != null) {
                this.qiniuFileSystem.getNodeByPath(this.objectName, false, false);
            }
            return true;
        } catch (QiniuFileSystem.InvalidPathError e) {
            return false;
        }
    }

    @Nonnull
    @Override
    public VirtualFile[] list() throws IOException {
        LOG.log(Level.INFO, "QiniuFile::{0}::list()", this.objectName);
        this.qiniuFileSystem.mayThrowIOException();
        QiniuFileSystem.DirectoryNode currentNode = this.qiniuFileSystem.getRootNode();
        if (this.objectName != null) {
            currentNode = this.qiniuFileSystem.getDirectoryNodeByPath(this.objectName, false);
        }
        final Collection<QiniuFileSystem.Node> childrenNodes = currentNode.getChildrenNodes();
        VirtualFile[] virtualFiles = new VirtualFile[childrenNodes.size()];
        int i = 0;
        for (QiniuFileSystem.Node childNode : childrenNodes) {
            Path path;
            if (this.objectName != null) {
                path = this.objectName.resolve(childNode.getNodeName());
            } else {
                path = FileSystems.getDefault().getPath(childNode.getNodeName());
            }
            virtualFiles[i] = new QiniuFile(this.qiniuFileSystem, path);
            i++;
        }
        return virtualFiles;
    }

    public boolean deleteRecursively() throws IOException {
        LOG.log(Level.INFO, "QiniuFile::{0}::deleteRecursively()", this.objectName);
        this.qiniuFileSystem.mayThrowIOException();
        if (this.qiniuFileSystem.getRootNode().getChildrenCount() == 0) {
            return false;
        }
        this.qiniuFileSystem.deleteAll();
        return true;
    }

    @Nonnull
    @Override
    public VirtualFile child(@Nonnull String childName) {
        LOG.log(Level.INFO, "QiniuFile::{0}::child({1})", new Object[]{this.objectName, childName});
        Path path;
        if (this.objectName != null) {
            path = this.objectName.resolve(childName);
        } else {
            path = FileSystems.getDefault().getPath(childName);
        }
        return new QiniuFile(this.qiniuFileSystem, path);
    }

    @Override
    public long length() throws IOException {
        LOG.log(Level.INFO, "QiniuFile::{0}::length()", this.objectName);
        QiniuFileSystem.Node currentNode = this.qiniuFileSystem.getRootNode();
        if (this.objectName != null) {
            currentNode = this.qiniuFileSystem.getNodeByPath(this.objectName, false, false);
        }
        if (currentNode == null) {
            return 0;
        } else if (currentNode.isFile()) {
            return ((QiniuFileSystem.FileNode) currentNode).getMetadata().fsize;
        } else {
            return ((QiniuFileSystem.DirectoryNode) currentNode).getChildrenCount();
        }
    }

    @Override
    public long lastModified() throws IOException {
        LOG.log(Level.INFO, "QiniuFile::{0}::lastModified()", this.objectName);
        QiniuFileSystem.Node currentNode = this.qiniuFileSystem.getRootNode();
        if (this.objectName != null) {
            currentNode = this.qiniuFileSystem.getNodeByPath(this.objectName, false, false);
        }
        if (currentNode != null && currentNode.isFile()) {
            return ((QiniuFileSystem.FileNode) currentNode).getMetadata().putTime / 10000;
        } else {
            return 0;
        }
    }

    @Override
    public boolean canRead() throws IOException {
        LOG.log(Level.INFO, "QiniuFile::{0}::canRead()", this.objectName);
        QiniuFileSystem.Node currentNode = this.qiniuFileSystem.getRootNode();
        if (this.objectName != null) {
            currentNode = this.qiniuFileSystem.getNodeByPath(this.objectName, false, false);
        }
        return currentNode != null;
    }

    @Nonnull
    @Override
    public InputStream open() throws IOException {
        LOG.log(Level.INFO, "QiniuFile::{0}::open()", this.objectName);
        return this.toExternalURL().openStream();
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        SerializeUtils.serializePath(out, this.objectName);
        out.writeObject(this.qiniuFileSystem);
        LOG.log(Level.INFO, "QiniuFile::{0}::writeObject()", this.objectName);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        this.objectName = SerializeUtils.deserializePath(in);
        this.qiniuFileSystem = (QiniuFileSystem) in.readObject();
        LOG.log(Level.INFO, "QiniuFile::{0}::readObject()", this.objectName);
    }
}
