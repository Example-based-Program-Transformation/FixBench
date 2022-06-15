package com.publicuhc.commands.routing;

import com.google.common.collect.MutableClassToInstanceMap;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.publicuhc.commands.proxies.CommandProxy;
import com.publicuhc.commands.proxies.TabCompleteProxy;
import com.publicuhc.commands.requests.CommandRequest;
import com.publicuhc.commands.requests.CommandRequestBuilder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class DefaultRouter implements Router {

    /**
     * Stores all the command proxies
     */
    private final ArrayList<CommandProxy> m_commands = new ArrayList<CommandProxy>();

    /**
     * Stores all the tab complete proxies
     */
    private final ArrayList<TabCompleteProxy> m_tabCompletes = new ArrayList<TabCompleteProxy>();

    /**
     * Store the instances of the classes to use
     */
    private final MutableClassToInstanceMap m_instances = MutableClassToInstanceMap.create();

    /**
     * Used to build requests
     */
    private final Provider<CommandRequestBuilder> m_requestProvider;

    @Inject
    public DefaultRouter(Provider<CommandRequestBuilder> requestProvider){
        m_requestProvider = requestProvider;
    }

    @Nullable
    @Override
    public List<CommandProxy> getCommandProxy(Command command, String parameters) {
        List<CommandProxy> proxies = new ArrayList<CommandProxy>();
        for(CommandProxy proxy : m_commands){
            if(command.getName().equals(proxy.getBaseCommand().getName())){
                if(proxy.doParamsMatch(parameters)){
                    proxies.add(proxy);
                }
            }
        }
        return proxies;
    }

    @Override
    public List<TabCompleteProxy> getTabCompleteProxy(Command command, String parameters) {
        List<TabCompleteProxy> proxies = new ArrayList<TabCompleteProxy>();
        for(TabCompleteProxy proxy : m_tabCompletes){
            if(command.getName().equals(proxy.getBaseCommand().getName())){
                if(proxy.doParamsMatch(parameters)){
                    proxies.add(proxy);
                }
            }
        }
        return proxies;
    }

    @Override
    public void registerCommands(Class clazz) {
        //TODO
    }

    @Override
    public void registerCommands(Object object) {
        //TODO
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        //Put all of the arguments into a string to match
        StringBuilder stringBuilder = new StringBuilder();
        for(String arg : args){
            stringBuilder.append(arg).append(" ");
        }

        //get all the proxies that match the route
        List<CommandProxy> proxies = getCommandProxy(command, stringBuilder.toString());

        //no proxies found that matched the route
        if(proxies == null){
            //TODO have a way to set message per command if no route exists
            return false;
        }

        //trigger all the proxies
        for(CommandProxy proxy : proxies){
            CommandRequestBuilder builder = m_requestProvider.get();
            CommandRequest request = builder.setCommand(command)
                    .setArguments(args)
                    .setSender(sender)
                    .build();
            proxy.trigger(request);
        }

        //don't print the error message
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        //Put all of the arguments into a string to match
        StringBuilder stringBuilder = new StringBuilder();
        for(String arg : args){
            stringBuilder.append(arg).append(" ");
        }

        //get all the proxies that match the route
        List<TabCompleteProxy> proxies = getTabCompleteProxy(command, stringBuilder.toString());

        //no proxies found that matched the route
        if(proxies == null){
            return new ArrayList<String>(0);
        }

        //trigger all the proxies and merge them
        List<String> results = new ArrayList<String>();
        for(TabCompleteProxy proxy : proxies){
            CommandRequestBuilder builder = m_requestProvider.get();
            CommandRequest request = builder.setCommand(command)
                    .setArguments(args)
                    .setSender(sender)
                    .build();
            results.addAll(proxy.trigger(request));
        }

        return results;
    }
}
