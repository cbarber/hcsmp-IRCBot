package com.psychobit.ircbot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import jerklib.Channel;
import jerklib.ConnectionManager;
import jerklib.Profile;
import jerklib.Session;
import jerklib.events.IRCEvent;
import jerklib.events.IRCEvent.Type;
import jerklib.events.InviteEvent;
import jerklib.events.MessageEvent;
import jerklib.events.NickListEvent;
import jerklib.listeners.IRCEventListener;

/**
 * IRC Connection Instance
 * @author psychobit
 *
 */
public class IRCConnection implements IRCEventListener
{
	
	/**
	 * Parent plugin
	 */
	private IRCBot _parent;

	/**
	 * Connection information
	 */
	private String _host;
	private String _username;
	private String _auth;
	private Boolean _debug;
	private ArrayList<ChannelInfo> _channels;
	
	/**
	 * Connection
	 */
	private ConnectionManager _manager;
	
	/**
	 * Set the connection details
	 * @param host Host to connect to
	 * @param username Username to log in as
	 * @param auth Nickserv auth password
	 * @param debug Debug toggle
	 */
	public IRCConnection( IRCBot plugin, String host, String username, String auth, Boolean debug )
	{
		// Assign values
		this._parent = plugin;
		this._host = host;
		this._username = username;
		this._auth = auth;
		this._debug = debug;
		
		// Create channel list
		this._channels = new ArrayList<ChannelInfo>();
	}
	
	/**
	 * Add a channel to the list
	 * @param channel IRC Channel to send to
	 * @param ingame In-game channel to send from
	 * @param color Color to set usernames to
	 */
	public void addChannel( String channel, String password, String ingame, int color )
	{
		this._channels.add( new ChannelInfo( channel, password, ingame, color ) );
	}
	
	/**
	 * Connect to the server
	 */
	public void connect()
	{
		this._manager = new ConnectionManager( new Profile( this._username ) );
		Session session = _manager.requestConnection( this._host );
		session.setRejoinOnKick( false );
		session.addIRCEventListener( this );
	}
	
	/**
	 * Disconnect from the server
	 */
	public void disconnect()
	{
		Iterator<Session> sessions = this._manager.getSessions().iterator();
		while( sessions.hasNext() ) sessions.next().close("Disconnecting!");
	}
	
	
	/**
	 * Stores information about a channel
	 * @author psychobit
	 *
	 */
	private class ChannelInfo
	{
		public String channel;
		public String password;
		public String ingame;
		public int color;
		
		/**
		 * Set the data
		 * @param channel IRC Channel to send to
		 * @param ingame In-game channel to send from
		 * @param color Color to set usernames to 
		 */
		public ChannelInfo( String channel, String password, String ingame, int color )
		{
			this.channel = channel;
			this.password = password;
			this.ingame = ingame;
			this.color = color;
		}
	}


	/**
	 * IRC event handler
	 */
	@Override
	public void receiveEvent( IRCEvent e )
	{
		if ( e.getType() == Type.CONNECT_COMPLETE )
		{
			// Auth with Nickserv and connect to channels
			Session session = e.getSession();
			session.sayPrivate( "Nickserv", "identify " + this._auth );
			for( int i = 0; i < this._channels.size(); i++ )
			{
				session.join( this._channels.get( i ).channel, this._channels.get( i ).password );
				session.sayPrivate( "Chanserv", "invite " + this._channels.get( i ).channel );
			}
			System.out.println( "[IRCBot] Connected!" );
		} else if ( e.getType() == Type.INVITE_EVENT ) {
			// Join channel if it is on our list
			InviteEvent ie = ( InviteEvent ) e;
			String channel = ie.getChannelName();
			for( int i = 0; i < this._channels.size(); i++ ) if ( this._channels.get( i ).channel.equalsIgnoreCase( channel ) ) e.getSession().join( channel, this._channels.get( i ).password );
		} else if ( e.getType() == Type.CHANNEL_MESSAGE ) {
			// Alias
			MessageEvent me = ( MessageEvent ) e;
			String msg = me.getMessage().replace( '§', ' ' );
			String channel = me.getChannel().getName();
			
			// Check for commands
			if ( msg.equalsIgnoreCase( ".ircwho" ) )
			{
				// Get all the players
				ArrayList<String> players = new ArrayList<String>();
				for( int i = 0; i < this._channels.size(); i++ )
				{
					if ( this._channels.get( i ).channel.equalsIgnoreCase( channel ) )
					{
						players.addAll( this._parent.getHerochatPlayers( this._channels.get( i ).ingame ) );
					}
				}
				
				// Remove duplicates
				HashSet<String> hs = new HashSet<String>();
				hs.addAll( players );
				players.clear();
				players.addAll(hs);
				
				// Assemble an output string and say it to the channel
				String output = "Currently in in-game IRC channel: ";
				for (String s : players ) output += s + ",";
				me.getChannel().say( output.substring( 0, output.length() - 1 ) );
				return;
			}
			
			// Shorten links and alias nickname
			msg = this._parent.shortenLinks( msg );
			String nick = me.getNick();
			
			// Pass the message on to herochat channels
			for( int i = 0; i < this._channels.size(); i++ )
			{
				if ( this._channels.get( i ).channel.equalsIgnoreCase( channel ) )
				{
					this._parent.ircToHerochat( nick, msg, this._channels.get( i ).ingame, this._channels.get( i ).color  );
				}
			}
		} else if ( e.getType() == Type.NICK_LIST_EVENT ) {
			// Get the list of people
			NickListEvent ne = ( NickListEvent ) e;
			String output = "Currently in IRC: ";
			List<String> players = ne.getNicks();
			for (String s : players ) if ( !s.equals( this._username ) ) output += s + ",";
			
			// Send to herochat
			String channel = ne.getChannel().getName();
			for( int i = 0; i < this._channels.size(); i++ )
			{
				if ( this._channels.get( i ).channel.equalsIgnoreCase( channel ) )
				{
					this._parent.ircToHerochat( "", output.substring( 0, output.length() - 1 ), this._channels.get( i ).ingame, 0 );
				}
			}
		} else if ( this._debug ) {
			System.out.println( "[IRCBot] " + e.getRawEventData() );
		}
	}
	
	
	/**
	 * Send ingame chat to IRC
	 * @param nick Player's name
	 * @param msg Message to send
	 * @param channel Channel to send to
	 */
	public void sendChat( String nick, String msg, String channel )
	{
		for( int i = 0; i < this._channels.size(); i++ )
		{
			if ( this._channels.get( i ).ingame.equalsIgnoreCase( channel ) )
			{
				if ( msg.equals( ".ircwho" ) ) this._manager.getSession( this._host ).sayRaw( "NAMES " + this._channels.get( i ).channel );
				else {
					Channel ch = this._manager.getSession( this._host ).getChannel( this._channels.get( i ).channel );
					ch.say( "[" + nick + "]: " + msg );
				}
				
			}
		}
	}
}
