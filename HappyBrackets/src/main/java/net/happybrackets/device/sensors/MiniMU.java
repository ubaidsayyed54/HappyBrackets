package net.happybrackets.device.sensors;

import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;
import net.beadsproject.beads.data.DataBead;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

public class MiniMU extends Sensor {

	@Override
	public String getSensorName() {
		return "MiniMU";
	}

	/**
	 * Specific listener for the MiniMu sensor. Listens to various MiniMu data: accelerometer, gyro, mag, tem. There is also an imu() callback which receives them all.
	 */
	public static abstract class MiniMUListener implements SensorListener {
		public void accelData(double x, double y, double z) {}
		public void gyroData(double x, double y, double z) {}
		public void magData(double x, double y, double z) {}
		public void imuData(double x, double y, double z,double x2, double y2, double z2,double x3, double y3, double z3) {}
		public void tempData(double t) {}
		@Override
		public void getData(DataBead db) {
			new Exception("Method getData(DataBead) not implemented.").printStackTrace();
		}	//not implemented
		@Override
		public void getSensor(DataBead db) {
			new Exception("Method getSensor(DataBead) not implemented.").printStackTrace();
		}	//not implemented
	}

	//TODO need to adjust for different versions

	private byte MAG_ADDRESS;
	private byte ACC_ADDRESS;
	private byte GYR_ADDRESS;

	private final static int MAG_DATA_ADDR = 0xa8;
	private final static int GYRO_DATA_ADDR = 0xa8;
	private final static int ACC_DATA_ADDR = 0xa8;

	private I2CBus bus;
	private I2CDevice gyrodevice, acceldevice, magdevice;

	private DataBead db2 = new DataBead();

	public MiniMU () {
		db2.put("Name","MiniMU-9");
		db2.put("Manufacturer","Pololu");
		// Work out which one we are
		// use MINIMUAHRS code to work out different versions.
		// use WHO_AM_I register to getInstance

		try {
			System.out.print("Getting I2C Bus 1:");
			bus = I2CFactory.getInstance(I2CBus.BUS_1);
			System.out.println(" Connected to bus OK!");

		} catch(IOException e) {
			System.out.println("Could not connect to bus!");
		}

		if (bus != null) {
			try {
				System.out.println("Trying a v2.");

				//  v2 info
				MAG_ADDRESS = 0x1e;
				ACC_ADDRESS = 0x19;
				GYR_ADDRESS = 0x6b;
				gyrodevice = bus.getDevice(GYR_ADDRESS);
				acceldevice = bus.getDevice(ACC_ADDRESS);
				magdevice = bus.getDevice(MAG_ADDRESS);

			} catch (IOException e) {
				System.out.println("OK - not a v2, so I'll try to set up a v3.");
			}
			try {
				//  v3 info
				MAG_ADDRESS = 0x1d;
				ACC_ADDRESS = 0x1d;
				GYR_ADDRESS = 0x6b;
				gyrodevice = bus.getDevice(GYR_ADDRESS);
				acceldevice = bus.getDevice(ACC_ADDRESS);
				magdevice = bus.getDevice(MAG_ADDRESS);

				System.out.println("OK - v3 set up.");

			} catch (Exception e2) {
				System.out.println("OK - v3 IOException as well. Not sure we have a Minimu v2 or v3 attached. ");
			}

		}
		try {

			byte CNTRL1_gyr = 0x20;
			byte CNTRL4_gyr = 0x23;
			byte CNTRL1_acc = 0x20;
			byte CNTRL4_acc = 0x23;
			byte CNTRL1_mag = 0x00;
			byte CNTRL2_mag = 0x01;
			byte CNTRL3_mag = 0x02;
			byte gyroSettings1 = 0b00001111;
			byte gyroSettings4 = 0b00110000;
			byte accSettings1 = 0b01000111;
			byte accSettings4 = 0b00101000;
			byte magSettings1 = 0b00001100;
			byte magSettings2 = 0b00100000;
			byte magSettings3 = 0b00000000;

			// GYRO
			gyrodevice.write(CNTRL1_gyr, gyroSettings1);
			gyrodevice.write(CNTRL4_gyr, gyroSettings4);

			// ACCEL
			acceldevice.write(CNTRL1_acc, accSettings1);
			acceldevice.write(CNTRL4_acc, accSettings4);

			// COMPASS enable
			magdevice.write(CNTRL1_mag, magSettings1);// DO = 011 (7.5 Hz ODR)
			magdevice.write(CNTRL2_mag, magSettings2);// GN = 001 (+/- 1.3 gauss full scale)
			magdevice.write(CNTRL3_mag, magSettings3);// MD = 00 (continuous-conversion mode)
//	        //// LSM303DLHC Magnetometer (from the c code)
//
//	        // DO = 011 (7.5 Hz ODR)
//	        writeMagReg(LSM303_CRA_REG_M, 0b00001100);
//			    #define LSM303_CRA_REG_M         0x00 // LSM303DLH, LSM303DLM, LSM303DLHC
//	        // GN = 001 (+/- 1.3 gauss full scale)
//	        writeMagReg(LSM303_CRB_REG_M, 0b00100000);
//              #define LSM303_CRB_REG_M         0x01 // LSM303DLH, LSM303DLM, LSM303DLHC

			//	        // MD = 00 (continuous-conversion mode)
//	        writeMagReg(LSM303_MR_REG_M, 0b00000000);
//    			#define LSM303_MR_REG_M  0x02 // LSM303DLH, LSM303DLM, LSM303DLHC

		} catch(IOException e) {
			System.out.println("Warning: unable to communicate with the MiniMU, we're not going to be getting any sensor data :-(");
		}
		if (bus != null & acceldevice != null) {
			start();
		}
	}

	public void update() throws IOException {

		for (SensorListener sListener: listeners){
			SensorListener sl = (SensorListener) sListener;

			DataBead db = new DataBead();
			db.put("Accelerator",this.readSensorsAccel());
			db.put("Gyrometer", this.readSensorsGyro());
			db.put("Magnetometer", this.readSensorsMag());

			sl.getData(db);
			sl.getSensor(db2);

		}
	}

	private void start() {
		Runnable task = new Runnable() {
			@Override
			public void run() {
				while(true) {
					try {
						float[] gyroData = readSensorsGyro();
						float[] accelData = readSensorsAccel();
						float[] magData = readSensorsMag();

						//pass data on to listeners
						for(SensorListener listener : listeners) {
							MiniMUListener muListener = (MiniMUListener)listener;
							if (accelData.length > 0 ){ // misc_tests for empty array.
								muListener.accelData(accelData[0], accelData[1], accelData[2]);
								muListener.gyroData(  gyroData[0],  gyroData[1],  gyroData[2]);
								muListener.magData(    magData[0],   magData[1],   magData[2]);
								muListener.imuData(  accelData[0], accelData[1], accelData[2],
										gyroData[0],  gyroData[1],  gyroData[2],
										magData[0],   magData[1],   magData[2]);
							}
						}
					} catch (IOException e) {
//						System.out.println("MiniMU not receiving data.");
					}
					try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		};
		new Thread(task).start();
	}

	private float[] readSensorsGyro() throws IOException {
		int numElements = 3; //
		float[] result = {0, 0, 0};
		int bytesPerElement = 2; // assuming short?
		int numBytes = numElements * bytesPerElement; //
		byte[] bytes = new byte[numBytes]; //
		DataInputStream accelIn;
		gyrodevice.read(0xa8, bytes, 0, bytes.length);
		accelIn = new DataInputStream(new ByteArrayInputStream(bytes));
		for (int i = 0; i < numElements; i++) {
			byte a = accelIn.readByte(); //least sig
			byte b = accelIn.readByte(); //most sig
			boolean[] abits = getBits(a);
			boolean[] bbits = getBits(b);
			boolean[] shortybits = new boolean[16];
			for(int j = 0; j < 8; j++) {
				shortybits[j] = bbits[j];
			}
			for (int j = 0; j < 8; j++) {
				shortybits[j + 8] = abits[j];
			}
			int theInt = bits2Int(shortybits);
			result[i] = theInt;
		}
		return result;
	}

	private float[] readSensorsAccel() throws IOException {
		int numElements = 3; //
		float[] result = {0, 0, 0};

		int bytesPerElement = 2; // assuming short?
		int numBytes = numElements * bytesPerElement; //
		byte[] bytes = new byte[numBytes]; //
		DataInputStream accelIn;
		acceldevice.read(0xa8, bytes, 0, bytes.length);
		accelIn = new DataInputStream(new ByteArrayInputStream(bytes));
		for (int i = 0; i < numElements; i++) {
			byte a = accelIn.readByte(); //least sig
			byte b = accelIn.readByte(); //most sig
			boolean[] abits = getBits(a);
			boolean[] bbits = getBits(b);
			boolean[] shortybits = new boolean[16];
			for(int j = 0; j < 8; j++) {
				shortybits[j] = bbits[j];
			}
			for(int j = 0; j < 8; j++) {
				shortybits[j + 8] = abits[j];
			}
			int theInt = bits2Int(shortybits);
			result[i] = theInt;
		}
		return result;
	}

	private float[] readSensorsMag() throws IOException {
		int numElements = 3; //
		float[] result = {0, 0, 0};
		int bytesPerElement = 2; // assuming short?
		int numBytes = numElements * bytesPerElement; //
		byte[] bytes = new byte[numBytes]; //
		DataInputStream magIn;
		magdevice.read(0x83, bytes, 0, bytes.length);
		magIn = new DataInputStream(new ByteArrayInputStream(bytes));
		for (int i = 0; i < numElements; i++) {
			byte a = magIn.readByte(); //least sig
			byte b = magIn.readByte(); //most sig
			boolean[] abits = getBits(a);
			boolean[] bbits = getBits(b);
			boolean[] shortybits = new boolean[16];
			// The mag sensor is BIG ENDIAN on the lsm303dlhc
			// so lets flip b and a compared to Acc
			for(int j = 0; j < 8; j++) {
				shortybits[j] = abits[j];
			}
			for(int j = 0; j < 8; j++) {
				shortybits[j + 8] = bbits[j];
			}
			int theInt = bits2Int(shortybits);
			result[i] = theInt;
		}
		return result;
	}

	private static boolean[] getBits(byte inByte) {
		boolean[] bits = new boolean[8];
		for (int j = 0; j < 8; j++) {
			// Shift each bit by 1 starting at zero shift
			byte tmp = (byte) (inByte >> j);
			// Check byte with mask 00000001 for LSB
			bits[7-j] = (tmp & 0x01) == 1;
		}
		return bits;
	}

	private static String bits2String(boolean[] bbits) {
		StringBuffer b = new StringBuffer();
		for(boolean v : bbits) {
			b.append(v?1:0);
		}
		return b.toString();
	}

	private static int bits2Int(boolean[] bbits) {
		int result = 0;
		int length = bbits.length - 1;
		if (bbits[0]) { // if the most significant bit is true
			for(int i = 0; i < length; i++) { //
				result -= bbits[length - i] ? 0 : Math.pow(2, i) ; // use the negative complement version
			}
		} else {
			for(int i = 0; i < length; i++) {
				result += bbits[length - i]? Math.pow(2, i) : 0; // use the positive version
			}
		}
		return result;
	}

	private static String byte2Str(byte inByte) {
		boolean[] bbits = getBits(inByte);
		return bits2String(bbits);
	}

}
