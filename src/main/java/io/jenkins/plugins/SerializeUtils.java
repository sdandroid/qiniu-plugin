package io.jenkins.plugins;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.FileSystems;
import java.nio.file.Path;

class SerializeUtils {
    static void serializePath(@Nonnull ObjectOutputStream out, @Nullable Path path) throws IOException {
        if (path != null) {
            out.writeInt(path.getNameCount());
            for (int i = 0; i < path.getNameCount(); i++) {
                out.writeObject(path.getName(i).toString());
            }
        } else {
            out.writeInt(0);
        }
    }

    @CheckForNull
    static Path deserializePath(@Nonnull ObjectInputStream in) throws IOException, ClassNotFoundException {
        final int objectNamePathSegmentsCount = in.readInt();
        if (objectNamePathSegmentsCount == 0) {
            return null;
        } else {
            final String firstSegment = (String) in.readObject();
            final String[] restSegments = new String[objectNamePathSegmentsCount - 1];
            for (int i = 0; i < objectNamePathSegmentsCount - 1; i++) {
                restSegments[i] = (String) in.readObject();
            }
            return FileSystems.getDefault().getPath(firstSegment, restSegments);
        }
    }
}
