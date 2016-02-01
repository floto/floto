package io.github.floto.core.util;

import com.google.common.base.Throwables;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class FileHashCache {
	Map<File, byte[]> hashMap = new HashMap<>();

	public byte[] getHash(File file) {
		byte[] hash = hashMap.get(file);
		if(hash != null) {
			return hash;
		}
		try(FileInputStream fis = new FileInputStream(file)) {
			Hasher hasher = Hashing.murmur3_32().newHasher();
			byte[] buffer = new byte[4096];
			int read;
			while((read = fis.read(buffer)) > -1) {
				hasher.putBytes(buffer, 0, read);
			}
			hash = hasher.hash().asBytes();
			hashMap.put(file, hash);
			return hash;
		} catch (IOException e) {
			throw Throwables.propagate(e);
		}

	}
}
