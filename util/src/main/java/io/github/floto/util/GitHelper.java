package io.github.floto.util;

import com.google.common.base.Throwables;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;

public class GitHelper {
    private final Repository repository;

    public GitHelper(File directory) {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        try {
            builder.findGitDir(directory.getAbsoluteFile());
            repository = builder.build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String describe() {
        try {
            Git git = Git.wrap(repository);
            String description = git.describe().call();
            // TODO: add commit id if actual tag
            if(description == null) {
                // Fallback
                description = repository.getRef("HEAD").getObjectId().abbreviate(7).name();
            }
            boolean clean = git.status().call().isClean();
            if(!clean) {
                description += "-mod";
            }
            return description;
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public String timestamp() {
        try {
            RevWalk revWalk = new RevWalk(repository);
            Ref headRef = repository.getRef("HEAD");
            RevCommit commit = revWalk.parseCommit(headRef.getObjectId());
            int commitTime = commit.getCommitTime();
            OffsetDateTime commitDate = LocalDateTime.ofEpochSecond(commitTime, 0, ZoneOffset.UTC).atOffset(ZoneOffset.UTC);
            return DateTimeFormatter.ISO_ZONED_DATE_TIME.format(commitDate);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
}
