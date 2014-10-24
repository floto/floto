package io.github.floto.core;

import static org.junit.Assert.assertEquals;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;

public class FlotoServiceTest {
	
	private FlotoService flotoService = new FlotoService();
	
	
	
	@Test
	public void testSplitImageNameNoRegistry() throws Exception {
		this.flotoService = new FlotoService() {
			@Override
			String getRegistryName() {
				return null;
			}
		};
		String imgName = "ubuntu:12.04";
		Pair<String, String> pair = this.flotoService.splitImageName(imgName);
		assertEquals("ubuntu", pair.getLeft());
		assertEquals("12.04", pair.getRight());
	}
	
	@Test
	public void testSplitImageNameNoRegistry2() throws Exception {
		this.flotoService = new FlotoService() {
			@Override
			String getRegistryName() {
				return "192.168.0.1:5000";
			}
		};
		String imgName = "ubuntu:12.04";
		Pair<String, String> pair = this.flotoService.splitImageName(imgName);
		assertEquals("ubuntu", pair.getLeft());
		assertEquals("12.04", pair.getRight());
	}
	
	@Test
	public void testSplitImageNameNoRegistry3() throws Exception {
		this.flotoService = new FlotoService() {
			@Override
			String getRegistryName() {
				return "192.168.0.1:5000";
			}
		};
		String imgName = "192.168.0.1:5000/dockerfile/ubuntu:12.04";
		Pair<String, String> pair = this.flotoService.splitImageName(imgName);
		assertEquals("192.168.0.1:5000/dockerfile/ubuntu", pair.getLeft());
		assertEquals("12.04", pair.getRight());
	}
	
	@Test
	public void testSplitImageNameNoRegistry4() throws Exception {
		this.flotoService = new FlotoService() {
			@Override
			String getRegistryName() {
				return "192.168.0.1:5000";
			}
		};
		String imgName = "192.168.0.1:5000/dockerfile/ubuntu";
		Pair<String, String> pair = this.flotoService.splitImageName(imgName);
		assertEquals("192.168.0.1:5000/dockerfile/ubuntu", pair.getLeft());
		assertEquals("latest", pair.getRight());
	}

}
