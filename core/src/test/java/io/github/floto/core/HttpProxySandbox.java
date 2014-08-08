package io.github.floto.core;

import io.github.floto.core.proxy.HttpProxy;

public class HttpProxySandbox {

    public static void main(String[] args) {
        new HttpProxySandbox().run();
    }

    private void run() {
        new HttpProxy(40005).start();
        while(true) {
            try {
                Thread.sleep(100000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
