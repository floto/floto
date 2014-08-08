package io.github.floto.dsl.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EsxHypervisorDescription extends HypervisorDescription {
    public String vCenter;
    public String esxHost;
    public String username;
    public String password;
    public String defaultDatastore;
    public List<String> networks = new ArrayList<>(Arrays.asList("VM Network"));

}
