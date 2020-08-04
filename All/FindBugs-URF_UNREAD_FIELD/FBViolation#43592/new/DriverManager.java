package com.capital;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.Augmenter;

public class DriverManager {
    private static ThreadLocal<WebDriver> webDriver = new ThreadLocal<WebDriver>();
    private static ThreadLocal<WebDriver> augmentedDriver = new ThreadLocal<WebDriver>();

    public static WebDriver getDriver() {
        return webDriver.get();
    }

    public static void setWebDriver(WebDriver driver) {
        webDriver.set(driver);
    }

    public static WebDriver getAugmentedDriver() {
        return augmentedDriver.get();
    }

    public static void setAugmentedWebDriver(WebDriver driver) {
        augmentedDriver.set(new Augmenter().augment(driver));
    }

}
