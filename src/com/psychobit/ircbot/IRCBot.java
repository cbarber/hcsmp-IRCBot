package com.psychobit.ircbot;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.dthielke.herochat.Channel;
import com.dthielke.herochat.ChannelManager;
import com.dthielke.herochat.Chatter;
import com.dthielke.herochat.Herochat;
import com.rosaloves.bitlyj.Bitly;
import com.rosaloves.bitlyj.BitlyException;
import com.rosaloves.bitlyj.Url;

public class IRCBot extends JavaPlugin implements Listener
{
	/**
	 * IRC server connections
	 */
	private ArrayList<IRCConnection> _connections;
	
	/**
	 * Connection switch
	 */
	private Boolean _connected;
	
	/**
	 * Set up event listeners and create irc connections
	 */
	public void onEnable()
	{
		this._connections = new ArrayList<IRCConnection>();
		this.getServer().getPluginManager().registerEvents( this, this );
		this.connectIRC();
	}
	
	/**
	 * Disconnect irc connections 
	 */
	public void onDisable()
	{
		this.disconnectIRC();
	}
	
	/**
	 * Connect to the IRC servers
	 */
	private void connectIRC()
	{
		// Get the latest config and set the debug flag
		this.reloadConfig();
		Boolean debug = this.getConfig().getBoolean( "debug", false );
		
		// Disconnect from servers
		this.disconnectIRC();
		
		// Add servers
		this._connections.clear();
		Iterator<String> servers = this.getConfig().getConfigurationSection( "servers" ).getKeys( false ).iterator();
		while ( servers.hasNext() )
		{
			// Create the 
			String server = servers.next();
			String host = this.getConfig().getString( "servers." + server + ".host" );
			String username = this.getConfig().getString( "servers." + server +  ".username" );
			String auth = this.getConfig().getString( "servers." + server + ".auth" );
			IRCConnection newServer = new IRCConnection( this, host, username, auth, debug );
			
			// Assign the channels
			Iterator<String> listeners = this.getConfig().getConfigurationSection( "servers." + server + ".listeners" ).getKeys( false ).iterator();
			while ( listeners.hasNext() )
			{
				String listener = listeners.next();
				String channel = this.getConfig().getString( "servers." + server + ".listeners." + listener + ".channel" );
				String password = this.getConfig().getString( "servers." + server + ".listeners." + listener + ".password" );
				String ingame = this.getConfig().getString( "servers." + server + ".listeners." + listener + ".ingame" );
				int color = this.getConfig().getInt( "servers." + server + ".listeners." + listener + ".color" );
				newServer.addChannel( channel, password, ingame, color );
			}
			
			// Connect to the server and add it to the list of connections
			newServer.connect();
			this._connections.add( newServer );
		}
		
		// Mark as connected
		this._connected = true;
	}
	
	/**
	 * Disconnect from the IRC servers 
	 */
	private void disconnectIRC()
	{
		Iterator<IRCConnection> connections = this._connections.iterator();
		while( connections.hasNext() ) connections.next().disconnect();
		this._connected = false;
	}
	
	
	/**
	 * Shorten URLs using the BitLY API 
	 * @param e Player chat event
	 */
	@EventHandler(priority=EventPriority.LOW)
	public void onPlayerChat( AsyncPlayerChatEvent e )
	{
		e.setMessage( this.shortenLinks( e.getMessage() ) );
	}
	
	/**
	 * Send a message to herochat
	 * @param nickname Nick of the IRC user who sent it
	 * @param msg Message they sent
	 * @param channel Herochat channel to send to
	 * @param color Color of the username
	 */
	public void ircToHerochat( String nickname, String msg, String channel, int color )
	{
		// Make sure the server is running Herochat
		Plugin p = this.getServer().getPluginManager().getPlugin( "Herochat" );
		if ( p == null || !( p instanceof Herochat ) ) return;
		
		// Send the chat to the channel
		ChannelManager channelManager = Herochat.getChannelManager();
		Channel ch = channelManager.getChannel( channel );
		if ( ch == null ) return;
		ch.announce( "§" + color  + nickname + "§7 | " + msg );
	}
	
	/**
	 * Get the list of players in a herochat channel
	 * @param channel Channel to list
	 * @return
	 */
	public ArrayList<String> getHerochatPlayers( String channel )
	{
		ArrayList<String> output = new ArrayList<String>();
		
		// Make sure the server is running Herochat
		Plugin p = this.getServer().getPluginManager().getPlugin( "Herochat" );
		if ( p == null || !( p instanceof Herochat ) ) return output;
		
		// Grab all the names
		ChannelManager channelManager = Herochat.getChannelManager();
		Channel ch = channelManager.getChannel( channel );
		if ( ch == null ) return output;
		Iterator<Chatter> iterator = ch.getMembers().iterator();
		while( iterator.hasNext() ) output.add( iterator.next().getName() );
		
		return output;
	}
	
	/**
	 * Shorten links via the BitLY API
	 * @param msg Input message
	 * @return
	 */
	public String shortenLinks( String msg )
	{
		// Alias 
		String APIuser = this.getConfig().getString( "bitly.username", "username" );
		String APIkey = this.getConfig().getString( "bitly.key", "key" );
		
		// Parse each "word" ( URLs cannot contain spaces )
		String [] parts = msg.split( "\\s" );
		for( String item : parts ) try
		{
			// Try to force it into a URL object. It will throw a MalformedURLException if it was not a URL 
			new URL( item );
			
			// Try to shorten the URL using BitLY
            Url url = Bitly.as( APIuser, APIkey ).call( Bitly.shorten( item ) );
            msg = msg.replaceAll( Pattern.quote( item ), url.getShortUrl() );
        } catch ( MalformedURLException ex ) {
            // Ignore things that aren't URLs
        } catch ( BitlyException ex ) {
        	System.out.println( "Failed to shorten URL: " + ex.getMessage() );
        }
		
		return msg;
	}
	
	/**
	 * Send a message to IRC
	 * @param e
	 */
	@EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true )
	public void playerChat( AsyncPlayerChatEvent e )
	{	
		// Make sure the server is running Herochat
		Plugin p = this.getServer().getPluginManager().getPlugin( "Herochat" );
		if ( p == null || !( p instanceof Herochat ) ) return;
		
		// Send the chat to IRC
		Player player = e.getPlayer();
		String playerName = player.getName();
		String msg = this.shortenLinks( e.getMessage() );
		Channel ch = Herochat.getChatterManager().getChatter( player ).getActiveChannel();
		Iterator<IRCConnection> connections = this._connections.iterator();
		while( connections.hasNext() ) connections.next().sendChat( playerName, msg, ch.getName() );
	}
	
	/**
	 * Process comamnds
	 */
	public boolean onCommand( CommandSender sender, Command command, String label, String[] args )
	{
		if ( args.length == 0 ) return true;
		
		// Reload the bot
		if ( args[0].equalsIgnoreCase( "reload" ) )
		{
			// Permission check
			if ( !sender.hasPermission( "ircbot.reload" ) )
			{
				sender.sendMessage( ChatColor.RED + "You don't have permission to do that!" );
				return true;
			}
			
			this.disconnectIRC();
			this.connectIRC();
			sender.sendMessage( "IRCBot reloaded!" );
			return true;
		}
		
		// Enable the bot
		if ( args[0].equalsIgnoreCase( "enable" ) )
		{
			// Permission check
			if ( !sender.hasPermission( "ircbot.toggle" ) )
			{
				sender.sendMessage( ChatColor.RED + "You don't have permission to do that!" );
				return true;
			}
			
			// Status check
			if ( this._connected )
			{
				sender.sendMessage( "IRCBot already enabled!" );
				return true;
			}
			
			this.connectIRC();
			sender.sendMessage( "IRCBot enabled!" );
			return true;
		}
		
		// Disable the bot
		if ( args[0].equalsIgnoreCase( "disable" ) )
		{
			// Permission check
			if ( !sender.hasPermission( "ircbot.toggle" ) )
			{
				sender.sendMessage( ChatColor.RED + "You don't have permission to do that!" );
				return true;
			}
			
			// Status check
			if ( !this._connected )
			{
				sender.sendMessage( "IRCBot already disabled!" );
				return true;
			}
			
			this.disconnectIRC();
			sender.sendMessage( "IRCBot disabled!" );
			return true;
		}
		
		// Check if the bot is enabled
		if ( args[0].equalsIgnoreCase( "status" ) )
		{
			if ( this._connected ) sender.sendMessage( "IRCBot is enabled" );
			else sender.sendMessage( "IRCBot is disabled" );
			return true;
		}
		return true;
	}
	
	@EventHandler
	public void onPlayerCommandPreprocess( PlayerCommandPreprocessEvent e )
	{
		String input = e.getMessage().substring( 1 );
		String[] args = input.split( " " );
		Channel channel = Herochat.getChannelManager().getChannel( args[0] );
		if ( ( channel != null ) && ( channel.isShortcutAllowed() ) )
		{
			args[0] = "";
			String playerName = e.getPlayer().getName();
			String msg = this.shortenLinks( StringUtils.join( args, " " ) );
			Iterator<IRCConnection> connections = this._connections.iterator();
			while( connections.hasNext() ) connections.next().sendChat( playerName, msg, channel.getName() );
		}
	}
}