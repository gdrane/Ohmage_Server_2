///*
// * To change this template, choose Tools | Templates
// * and open the template in the editor.
// */
//package edu.ucla.cens.pdc.libpdc.core;
//
//import edu.ucla.cens.pdc.libpdc.Constants;
//import edu.ucla.cens.pdc.libpdc.stream.DataStream;
//import edu.ucla.cens.pdc.libpdc.util.Log;
//import org.ccnx.ccn.protocol.ContentName;
//import org.ccnx.ccn.protocol.Interest;
//
///**
// *
// * @author Derek Kulinski
// */
//public class PDVControlCommands extends GenericCommand {
//	PDVControlCommands()
//	{
//		super(Constants.STR_CONTROL);
//	}
//
//	@Override
//	boolean processCommand(ContentName postfix, Interest interest)
//	{
//		GlobalConfig config = GlobalConfig.getInstance();
//		int components;
//		String command;
//		ContentName stream_uri;
//		DataStream ds;
//
//		components = postfix.count();
//		if (components < 1) {
//			Log.info("Missing command");
//			return false;
//		}
//
//		command = postfix.stringComponent(0);
//
//		//Right now all commands require an URI argument
//		if (components < 2) {
//			Log.info("Invalid arguments");
//			return false;
//		}
//
//		stream_uri = postfix.subname(1, components - 1);
//
//		ds = config.uri2dataStream(stream_uri);
//		if (ds == null) {
//			Log.info("Unknown stream");
//			return false;
//		}
//
//		Log.error("Unknown command '" + command + "'!");
//
//		return false;
//	}
//}
