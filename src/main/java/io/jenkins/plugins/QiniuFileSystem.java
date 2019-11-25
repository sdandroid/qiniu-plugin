package io.jenkins.plugins;

import com.qiniu.storage.BucketManager;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.model.FileInfo;
import com.qiniu.storage.model.FileListing;
import com.qiniu.util.Auth;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.*;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

class QiniuFileSystem implements Serializable {
    private static final Logger LOG = Logger.getLogger(QiniuFileSystem.class.getName());

    private String accessKey, secretKey, bucketName, downloadDomain;
    private Path objectNamePrefix;
    private boolean useHTTPs;
    private DirectoryNode rootNode;
    private IOException ioException;

    QiniuFileSystem(@Nonnull String accessKey, @Nonnull String secretKey, @Nonnull String bucketName,
                    @Nonnull Path objectNamePrefix, @Nonnull String downloadDomain, boolean useHTTPs) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.bucketName = bucketName;
        this.objectNamePrefix = objectNamePrefix;
        this.downloadDomain = downloadDomain;
        this.useHTTPs = useHTTPs;
        this.rootNode = new DirectoryNode("", this, null);
        initNodes();
    }

    QiniuFileSystem(@Nonnull String accessKey, @Nonnull String secretKey, @Nonnull String bucketName,
                    @Nonnull String downloadDomain, boolean useHTTPs, @Nonnull IOException ioException) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.bucketName = bucketName;
        this.objectNamePrefix = null;
        this.downloadDomain = downloadDomain;
        this.useHTTPs = useHTTPs;
        this.rootNode = new DirectoryNode("", this, null);
        this.ioException = ioException;
    }

    static QiniuFileSystem create(@Nonnull String accessKey, @Nonnull String secretKey, @Nonnull String bucketName,
                                  @Nonnull String objectNamePrefix, @Nonnull String downloadDomain, boolean useHTTPs) {
        try {
            return new QiniuFileSystem(accessKey, secretKey, bucketName, toPath(objectNamePrefix), downloadDomain, useHTTPs);
        } catch (InvalidPathError e) {
            return new QiniuFileSystem(accessKey, secretKey, bucketName, downloadDomain, useHTTPs, e);
        }
    }

    private void initNodes() {
        LOG.log(Level.INFO, "QiniuFileSystem::{0}::list()", this.objectNamePrefix);
        final BucketManager bucketManager = getBucketManager();
        String marker = null;
        String prefix = this.objectNamePrefix.toString();
        if (!prefix.endsWith(File.separator)) {
            prefix += File.separator;
        }
        try {
            for (;;) {
                LOG.log(Level.INFO, "QiniuFileSystem::{0}::list(), prefix={1}, marker={2}",
                        new Object[]{this.objectNamePrefix, prefix, marker});
                final FileListing list = bucketManager.listFiles(this.bucketName, prefix, marker, 1000, null);
                if (list.items != null) {
                    for (FileInfo metadata : list.items) {
                        final Path path = this.objectNamePrefix.relativize(toPath(metadata.key));
                        LOG.log(Level.INFO, "QiniuFileSystem::{0}::list(), key={1}",
                                new Object[]{this.objectNamePrefix, path.toString()});
                        this.createFileNodeByPath(path, metadata);
                    }
                }
                marker = list.marker;
                if (marker == null || marker.isEmpty()) {
                    break;
                }
            }
        } catch (IOException e) {
            LOG.log(Level.INFO, "QiniuFileSystem::{0}::list() error: {1}", new Object[]{this.objectNamePrefix, e});
            this.ioException = e;
        }
        LOG.log(Level.INFO, "QiniuFileSystem::{0}::list() done", this.objectNamePrefix);
    }

    @CheckForNull
    Node getNodeByPath(@Nonnull Path path, boolean createDirectory, boolean createNodeAsDirectory) throws InvalidPathError {
        DirectoryNode currentNode = this.rootNode;
        for (int i = 0; i < path.getNameCount(); i++) {
            final String currentNodeName = path.getName(i).toString();
            Node newCurrentNode = currentNode.getByName(currentNodeName);
            if (i < path.getNameCount() - 1) {
                if (newCurrentNode != null && newCurrentNode.isDirectory()) {
                    currentNode = (DirectoryNode) newCurrentNode;
                } else if (createDirectory && newCurrentNode == null) {
                    currentNode = currentNode.addChildDirectoryNode(currentNodeName);
                    LOG.log(Level.INFO, "create directory node: {0}", currentNode.getPath().toString());
                } else {
                    throw new InvalidPathError("Path " + path.toString() + " is invalid, file " + path.getName(i).toString() + " is not directory");
                }
            } else if (createNodeAsDirectory && newCurrentNode == null) {
                return currentNode.addChildDirectoryNode(currentNodeName);
            } else {
                return newCurrentNode;
            }
        }
        return currentNode;
    }

    @Nonnull
    FileNode getFileNodeByPath(@Nonnull Path path, boolean createDirectory) throws InvalidPathError {
        final Node node = this.getNodeByPath(path, createDirectory, false);
        if (node != null && node.isFile()) {
            return (FileNode) node;
        } else {
            throw new InvalidPathError("Path " + path.toString() + " is invalid, file " + path.getFileName() + " is not a file");
        }
    }

    @Nonnull
    DirectoryNode getDirectoryNodeByPath(@Nonnull Path path, boolean createDirectory) throws InvalidPathError {
        final Node node = this.getNodeByPath(path, createDirectory, createDirectory);
        if (node != null && node.isDirectory()) {
            return (DirectoryNode) node;
        } else {
            throw new InvalidPathError("Path " + path.toString() + " is invalid, file " + path.getFileName() + " is not a directory");
        }
    }

    @Nonnull
    DirectoryNode getParentNodeByPath(@Nonnull Path path, boolean createDirectory) throws InvalidPathError {
        Path parentPath = path.getParent();
        if (parentPath != null) {
            return this.getDirectoryNodeByPath(parentPath, createDirectory);
        } else {
            return this.rootNode;
        }
    }

    void createFileNodeByPath(@Nonnull Path path, @Nonnull FileInfo metadata) throws InvalidPathError {
        final Path childPath = path.getFileName();
        if (childPath != null) {
            final DirectoryNode parentNode = this.getParentNodeByPath(path, true);
            parentNode.addChildFileNode(childPath.toString(), metadata);
            LOG.log(Level.INFO, "create file node: {0}", path.toString());
        } else {
            throw new InvalidPathError("path must not be empty");
        }
    }

    void deleteFileNodeByPath(@Nonnull Path path) throws InvalidPathError, DeleteDirectoryError {
        LOG.log(Level.INFO, "delete file node: {0}", path.toString());
        DirectoryNode parentNode = this.getParentNodeByPath(path, false);
        final Path childPath = path.getFileName();
        if (childPath == null) {
            throw new InvalidPathError("path must not be empty");
        }
        final String childName = childPath.toString();
        final Node subNode = parentNode.getByName(childName);
        if (subNode != null && subNode.isFile()) {
            parentNode.removeChildNode(childName);
            while (parentNode.isEmpty()) {
                final String name = parentNode.getNodeName();
                parentNode = parentNode.getParentNode();
                if (parentNode == null) {
                    return;
                }
                parentNode.removeChildNode(name);
            }
        } else {
            throw new DeleteDirectoryError("Path " + path.toString() + " is not a file");
        }
    }

    void deleteAll() throws IOException {
        LOG.log(Level.INFO, "delete all nodes");
        final BucketManager bucketManager = getBucketManager();
        final BucketManager.BatchOperations batch = new BucketManager.BatchOperations();
        int counter = 0;
        String marker = null;
        String prefix = "";
        if (this.objectNamePrefix != null) {
            prefix = this.objectNamePrefix.toString();
        }

        if (!prefix.endsWith(File.separator)) {
            prefix += File.separator;
        }

        for (;;) {
            final FileListing list = bucketManager.listFiles(this.bucketName, prefix, marker, 1000, null);
            if (list.items != null) {
                for (FileInfo metadata : list.items) {
                    batch.addDeleteOp(this.bucketName, metadata.key);
                    counter++;
                    if (counter >= 1000) {
                        bucketManager.batch(batch);
                        batch.clearOps();
                        counter = 0;
                    }
                }
            }
            marker = list.marker;
            if (marker == null || marker.isEmpty()) {
                break;
            }
        }
        if (counter > 0) {
            bucketManager.batch(batch);
        }
        this.rootNode.childrenNodes.clear();
    }

    void mayThrowIOException() throws IOException {
        if (this.ioException != null) {
            throw this.ioException;
        }
    }

    static abstract class Node {
        final QiniuFileSystem fileSystem;
        final DirectoryNode parentNode;
        final String nodeName;

        Node(@Nonnull String nodeName, @Nonnull QiniuFileSystem fileSystem, DirectoryNode parentNode) {
            this.nodeName = nodeName;
            this.fileSystem = fileSystem;
            this.parentNode = parentNode;
        }

        @Nonnull
        String getNodeName() {
            return this.nodeName;
        }

        @Nonnull
        QiniuFileSystem getFileSystem() {
            return this.fileSystem;
        }

        @CheckForNull
        DirectoryNode getParentNode() {
            return this.parentNode;
        }

        boolean isFile() {
            return this.getClass().equals(FileNode.class);
        }

        boolean isDirectory() {
            return this.getClass().equals(DirectoryNode.class);
        }

        @Nonnull Path getPath() {
            final Node parentNode = this.getParentNode();
            if (parentNode != null) {
                return parentNode.getPath().resolve(this.nodeName);
            } else {
                return FileSystems.getDefault().getPath(this.nodeName);
            }
        }
    }

    static final class DirectoryNode extends Node {
        private final Map<String, Node> childrenNodes;

        DirectoryNode(@Nonnull String nodeName, @Nonnull QiniuFileSystem fileSystem, DirectoryNode parentNode) {
            super(nodeName, fileSystem, parentNode);
            this.childrenNodes = new HashMap<>();
        }

        @Nonnull
        DirectoryNode addChildDirectoryNode(String name) {
            final DirectoryNode childNode = new DirectoryNode(name, this.fileSystem, this);
            this.childrenNodes.put(name, childNode);
            return childNode;
        }

        @Nonnull
        FileNode addChildFileNode(String name, @Nonnull FileInfo metadata) {
            final FileNode childNode = new FileNode(name, metadata, this.fileSystem, this);
            this.childrenNodes.put(name, childNode);
            return childNode;
        }

        void removeChildNode(String name) {
            this.childrenNodes.remove(name);
        }

        @CheckForNull
        Node getByName(String name) {
            return this.childrenNodes.get(name);
        }

        boolean isEmpty() {
            return this.childrenNodes.isEmpty();
        }

        @Nonnull
        Collection<Node> getChildrenNodes() {
            return this.childrenNodes.values();
        }

        @Nonnull
        int getChildrenCount() {
            return this.childrenNodes.size();
        }
    }

    public static final class InvalidPathError extends FileNotFoundException {
        InvalidPathError(String detail) {
            super(detail);
        }
    }

    public static final class DeleteDirectoryError extends IOException {
        DeleteDirectoryError(String detail) {
            super(detail);
        }
    }

    static final class FileNode extends Node {
        private final FileInfo metadata;

        FileNode(@Nonnull String nodeName, @Nonnull FileInfo metadata,
                 @Nonnull QiniuFileSystem fileSystem, DirectoryNode parentNode) {
            super(nodeName, fileSystem, parentNode);
            this.metadata = metadata;
        }

        public FileInfo getMetadata() {
            return this.metadata;
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

    @Nonnull
    static Path toPath(@Nonnull String objectName) throws InvalidPathError {
        final String[] segments = objectName.split(Pattern.quote(File.separator));
        switch (segments.length) {
        case 0:
            throw new InvalidPathError("Path is empty");
        case 1:
            return FileSystems.getDefault().getPath(segments[0]);
        default:
            return FileSystems.getDefault().getPath(segments[0], Arrays.copyOfRange(segments, 1, segments.length));
        }
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

    public Path getObjectNamePrefix() {
        return this.objectNamePrefix;
    }

    @Nonnull
    public String getDownloadDomain() {
        return this.downloadDomain;
    }

    public boolean isUseHTTPs() {
        return useHTTPs;
    }

    @Nonnull
    public DirectoryNode getRootNode() {
        return this.rootNode;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeObject(this.accessKey);
        out.writeObject(this.secretKey);
        out.writeObject(this.bucketName);
        out.writeObject(this.downloadDomain);
        SerializeUtils.serializePath(out, this.objectNamePrefix);
        out.writeBoolean(this.useHTTPs);
        if (this.ioException != null) {
            out.writeBoolean(true);
            out.writeObject(this.ioException.getMessage());
        } else {
            out.writeBoolean(false);
        }
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        this.accessKey = (String) in.readObject();
        this.secretKey = (String) in.readObject();
        this.bucketName = (String) in.readObject();
        this.downloadDomain = (String) in.readObject();
        this.objectNamePrefix = SerializeUtils.deserializePath(in);
        this.useHTTPs = in.readBoolean();
        if (in.readBoolean()) {
            this.ioException = new IOException((String) in.readObject());
        } else {
            this.ioException = null;
        }
        this.rootNode = new DirectoryNode("", this, null);
        initNodes();
    }
}
