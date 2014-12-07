package io.github.floto.core.virtualization.virtualbox.util;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jersey.repackaged.com.google.common.collect.Maps;

import com.google.common.base.Splitter;

public final class VBoxManageUtil {
	
	static final String LINE_SEPARATOR = System.getProperty("line.separator");
	
	public static List<Map<String, String>> convertVBoxManageResult(String input) {
		List<String> entries = Splitter.on(LINE_SEPARATOR + LINE_SEPARATOR).omitEmptyStrings().trimResults().splitToList(input);
		List<Map<String, String>>  entriesAsMap = entries.stream().map(s -> {
			Map<String, String> map = Maps.newHashMap();
			Splitter.on(LINE_SEPARATOR).splitToList(s).forEach(t ->  {
				List<String> keyValues = Splitter.on(":").trimResults().omitEmptyStrings().splitToList(t);
				if(keyValues.size() >= 2) {
					map.put(keyValues.get(0), keyValues.get(1));
				}
			});
			return map;
		}).collect(Collectors.toList());
		return entriesAsMap;
	}

}
