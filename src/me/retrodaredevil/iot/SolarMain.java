package me.retrodaredevil.iot;

import me.retrodaredevil.iot.outhouse.OuthousePacketCreator;
import me.retrodaredevil.iot.packets.PacketCreator;
import me.retrodaredevil.iot.packets.PacketSaver;
import me.retrodaredevil.iot.solar.MatePacketCreator49;
import org.lightcouch.CouchDbException;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.UnsupportedCommOperationException;
import me.retrodaredevil.ProgramArgs;

public class SolarMain {
	private int connectSolar(ProgramArgs args) throws Exception {
		InputStream in = null;
		try {
			in = getInputStream(args);
		} catch (PortInUseException e){
			e.printStackTrace();
			System.err.println("That port is in use!");
		} catch (NoSuchPortException e){
			e.printStackTrace();
			System.err.println("No such port: '" + args.getPortName() + "'");
		}
		if(in == null){
			return 1;
		}
		connect(args, in, "solarthing", new MatePacketCreator49(args.getIgnoreCheckSum()));
		return 0;
	}
	private int connectOuthouse(ProgramArgs args) throws Exception {
		InputStream in = System.in;
		connect(args, in, "outhouse", new OuthousePacketCreator());
		return 0;
	}

	private void connect(ProgramArgs args, InputStream in, String databaseName, PacketCreator packetCreator) throws Exception {
		PacketSaver packetSaver;
		if(args.isLocal()){
			packetSaver = new JsonFilePacketSaver(args.getFilePath());
		} else {
			try {
				packetSaver = new CouchDbPacketSaver(args, databaseName);
			} catch (CouchDbException e) {
				e.printStackTrace();
				System.err.println("Unable to connect to database.");
				packetSaver = new JsonFilePacketSaver(args.getFilePath());
			}
		}
		Runnable run = new SolarReader(in, args.getThrottleFactor(), packetCreator, packetSaver);
		while(true){
			run.run();
		}
	}
	private InputStream getInputStream(ProgramArgs args) throws UnsupportedCommOperationException, IOException, PortInUseException, NoSuchPortException {
		if(args.isUnitTest()){
			return new BufferedInputStream(System.in);
		}

		final CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(args.getPortName());
		final CommPort commPort = portIdentifier.open(this.getClass().getName(), 2000);

		if (!(commPort instanceof SerialPort)) {
			throw new IllegalStateException("The port is not a serial port! It's a '" + commPort.getClass().getName() + "'");
		}
		final SerialPort serialPort = (SerialPort) commPort;
		serialPort.setSerialPortParams(19200, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);

		serialPort.setDTR(true);
		serialPort.setRTS(false);

		return new BufferedInputStream(serialPort.getInputStream()); // TODO, if we need to, we'll have to refactor this code a bit if we want to use the output stream
	}

	public static void main(String[] args) {
		ProgramArgs pArgs = new ProgramArgs(args);
		Program program = getProgram(pArgs.getParameters());
		if(pArgs.isHelp() || program == null){
			System.out.println("<command> {solar|outhouse}");
			System.out.println("Help was called. Check ProgramArgs.java. Self explainatory. Sorry I'm lazy.\n" +
					"Also note, as a VM argument, you should have -Djava.library.path=/usr/lib/jni");
			System.exit(1);
		}
		try {
			int status = 1;
			if(program == Program.SOLAR) {
				status = (new SolarMain()).connectSolar(pArgs);
			} else if(program == Program.OUTHOUSE){
				status = (new SolarMain()).connectOuthouse(pArgs);
			} else {
				System.out.println("Specify solar|outhouse");
			}
			System.exit(status);
		} catch (Throwable t) {
			t.printStackTrace();

			pArgs.printInJson();

			System.exit(1);
		}
	}
	private static Program getProgram(List<String> args){
		if(args.size() == 0){
			return null;
		}
		String program = args.get(0).toLowerCase();
		if(program.equals("solar")){
			return Program.SOLAR;
		} else if(program.equals("outhouse")){
			return Program.OUTHOUSE;
		}
		return null;
	}
	private enum Program {
		SOLAR, OUTHOUSE
	}

}