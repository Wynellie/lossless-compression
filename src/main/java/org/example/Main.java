package org.example;

import org.example.menu.ConsoleMenu;

public class Main {
    public static void main(String[] args) {
        new ConsoleMenu(System.in, System.out).run();
    }
}