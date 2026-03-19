package com.banknotouch;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class BankNoTouchPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(BankNoTouchPlugin.class);
        RuneLite.main(args);
    }
}
