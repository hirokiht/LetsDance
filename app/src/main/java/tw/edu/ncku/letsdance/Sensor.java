/**************************************************************************************************
  Filename:       Sensor.java
  Revised:        $Date: 2013-08-30 11:44:31 +0200 (fr, 30 aug 2013) $
  Revision:       $Revision: 27454 $

  Copyright (c) 2013 - 2014 Texas Instruments Incorporated

  All rights reserved not granted herein.
  Limited License. 

  Texas Instruments Incorporated grants a world-wide, royalty-free,
  non-exclusive license under copyrights and patents it now or hereafter
  owns or controls to make, have made, use, import, offer to sell and sell ("Utilize")
  this software subject to the terms herein.  With respect to the foregoing patent
  license, such license is granted  solely to the extent that any such patent is necessary
  to Utilize the software alone.  The patent license shall not apply to any combinations which
  include this software, other than combinations with devices manufactured by or for TI (�TI Devices�). 
  No hardware patent is licensed hereunder.

  Redistributions must preserve existing copyright notices and reproduce this license (including the
  above copyright notice and the disclaimer and (if applicable) source code license limitations below)
  in the documentation and/or other materials provided with the distribution

  Redistribution and use in binary form, without modification, are permitted provided that the following
  conditions are met:

    * No reverse engineering, decompilation, or disassembly of this software is permitted with respect to any
      software provided in binary form.
    * any redistribution and use are licensed by TI for use only with TI Devices.
    * Nothing shall obligate TI to provide you with source code for the software licensed and provided to you in object code.

  If software source code is provided to you, modification and redistribution of the source code are permitted
  provided that the following conditions are met:

    * any redistribution and use of the source code, including any resulting derivative works, are licensed by
      TI for use only with TI Devices.
    * any redistribution and use of any object code compiled from the source code and any resulting derivative
      works, are licensed by TI for use only with TI Devices.

  Neither the name of Texas Instruments Incorporated nor the names of its suppliers may be used to endorse or
  promote products derived from this software without specific prior written permission.

  DISCLAIMER.

  THIS SOFTWARE IS PROVIDED BY TI AND TI�S LICENSORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
  BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
  IN NO EVENT SHALL TI AND TI�S LICENSORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
  OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
  OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
  POSSIBILITY OF SUCH DAMAGE.


 **************************************************************************************************/
package tw.edu.ncku.letsdance;

import static tw.edu.ncku.letsdance.SensorTagGatt.*;
import static java.lang.Math.pow;

import java.util.List;
import java.util.UUID;


/**
 * This enum encapsulates the differences amongst the sensors. The differences include UUID values and how to interpret the
 * characteristic-containing-measurement.
 */
public enum Sensor {

  IR_TEMPERATURE(UUID_IRT_SERV, UUID_IRT_DATA, UUID_IRT_CONF, UUID_IRT_PERI) {
    @Override
    public float[] convert(final byte [] value) {

      /*
       * The IR Temperature sensor produces two measurements; Object ( AKA target or IR) Temperature, and Ambient ( AKA die ) temperature.
       * Both need some conversion, and Object temperature is dependent on Ambient temperature.
       * They are stored as [ObjLSB, ObjMSB, AmbLSB, AmbMSB] (4 bytes) Which means we need to shift the bytes around to get the correct values.
       */

      double ambient = extractAmbientTemperature(value);
      double target = extractTargetTemperature(value, ambient);
      return new float[]{(float)ambient, (float)target};
    }

    private double extractAmbientTemperature(byte [] v) {
      int offset = 2;
      return shortUnsignedAtOffset(v, offset) / 128.0;
    }

    private double extractTargetTemperature(byte [] v, double ambient) {
      Integer twoByteValue = shortSignedAtOffset(v, 0);

      double Vobj2 = twoByteValue.doubleValue();
      Vobj2 *= 0.00000015625;

      double Tdie = ambient + 273.15;

      double S0 = 5.593E-14; // Calibration factor
      double a1 = 1.75E-3;
      double a2 = -1.678E-5;
      double b0 = -2.94E-5;
      double b1 = -5.7E-7;
      double b2 = 4.63E-9;
      double c2 = 13.4;
      double Tref = 298.15;
      double S = S0 * (1 + a1 * (Tdie - Tref) + a2 * pow((Tdie - Tref), 2));
      double Vos = b0 + b1 * (Tdie - Tref) + b2 * pow((Tdie - Tref), 2);
      double fObj = (Vobj2 - Vos) + c2 * pow((Vobj2 - Vos), 2);
      double tObj = pow(pow(Tdie, 4) + (fObj / S), .25);

      return tObj - 273.15;
    }
  },

  ACCELEROMETER(UUID_ACC_SERV, UUID_ACC_DATA, UUID_ACC_CONF, UUID_ACC_PERI, (byte)3) {
  	@Override
  	public float[] convert(final byte[] value) {
  		/*
  		 * The accelerometer has the range [-2g, 2g] with unit (1/64)g.
  		 * To convert from unit (1/64)g to unit g we divide by 64.
  		 * (g = 9.81 m/s^2)
  		 * The z value is multiplied with -1 to coincide with how we have arbitrarily defined the positive y direction. (illustrated by the apps accelerometer
  		 * image)
  		 */
          Integer x = (int) value[0];
          Integer y = (int) value[1];
          Integer z = (int) value[2] * -1;
          final float SCALE = 64.0f;
          return new float[]{x / SCALE, y / SCALE, z / SCALE};
  	}
  },ACCELEROMETER4G(UUID_ACC_SERV, UUID_ACC_DATA, UUID_ACC_CONF, UUID_ACC_PERI, (byte)2) {
    @Override
    public float[] convert(final byte[] value) {
  		/*
  		 * The accelerometer has the range [-2g, 2g] with unit (1/64)g.
  		 * To convert from unit (1/64)g to unit g we divide by 64.
  		 * (g = 9.81 m/s^2)
  		 * The z value is multiplied with -1 to coincide with how we have arbitrarily defined the positive y direction. (illustrated by the apps accelerometer
  		 * image)
  		 */
        Integer x = (int) value[0];
        Integer y = (int) value[1];
        Integer z = (int) value[2] * -1;
        final float SCALE = 32.0f;
        return new float[]{x / SCALE, y / SCALE, z / SCALE};
    }
  },ACCELEROMETER2G(UUID_ACC_SERV, UUID_ACC_DATA, UUID_ACC_CONF, UUID_ACC_PERI, (byte)1) {
    @Override
    public float[] convert(final byte[] value) {
  		/*
  		 * The accelerometer has the range [-2g, 2g] with unit (1/64)g.
  		 * To convert from unit (1/64)g to unit g we divide by 64.
  		 * (g = 9.81 m/s^2)
  		 * The z value is multiplied with -1 to coincide with how we have arbitrarily defined the positive y direction. (illustrated by the apps accelerometer
  		 * image)
  		 */
      //	DeviceActivity da = DeviceActivity.getInstance();
        Integer x = (int) value[0];
        Integer y = (int) value[1];
        Integer z = (int) value[2] * -1;
        final float SCALE = 16.0f;
        return new float[]{x / SCALE, y / SCALE, z / SCALE};

    }
  },

  HUMIDITY(UUID_HUM_SERV, UUID_HUM_DATA, UUID_HUM_CONF, UUID_HUM_PERI){
    @Override
    public float[] convert(final byte[] value) {
      int a = shortUnsignedAtOffset(value, 2);
      // bits [1..0] are status bits and need to be cleared according
      // to the user guide, but the iOS code doesn't bother. It should
      // have minimal impact.
      a = a - (a % 4);

      return new float[]{(-6f) + 125f * (a / 65535f)};
    }
  },

  MAGNETOMETER(UUID_MAG_SERV, UUID_MAG_DATA, UUID_MAG_CONF, UUID_MAG_PERI) {
    @Override
    public float[] convert(final byte [] value) {
      // Multiply x and y with -1 so that the values correspond with the image in the app
      float x = shortSignedAtOffset(value, 0) * (2000f / 65536f) * -1;
      float y = shortSignedAtOffset(value, 2) * (2000f / 65536f) * -1;
      float z = shortSignedAtOffset(value, 4) * (2000f / 65536f);
      
      return new float[]{x, y, z};
    }
  },

  LUXOMETER(UUID_OPT_SERV, UUID_OPT_DATA, UUID_OPT_CONF, UUID_OPT_PERI) {
    @Override
    public float[] convert(final byte [] value) {
      int mantissa;
      int exponent;
      Integer sfloat= shortUnsignedAtOffset(value, 0);
      
      mantissa = sfloat & 0x0FFF;
      exponent = (sfloat >> 12) & 0xFF;

      double magnitude = pow(2.0f, exponent);
      float output = (float)(mantissa * magnitude);

			return new float[]{output / 100.0f};
    }
  },

  GYROSCOPE(UUID_GYR_SERV, UUID_GYR_DATA, UUID_GYR_CONF, UUID_GYR_PERI, (byte)7) {
    @Override
    public float[] convert(final byte [] value) {

      float y = shortSignedAtOffset(value, 0) * (500f / 65536f) * -1;
      float x = shortSignedAtOffset(value, 2) * (500f / 65536f);
      float z = shortSignedAtOffset(value, 4) * (500f / 65536f);
      
      return new float[]{x,y,z};
    }
  },
  GYROSCOPE_X(UUID_GYR_SERV, UUID_GYR_DATA, UUID_GYR_CONF, UUID_GYR_PERI, (byte)1) {
    @Override
    public float[] convert(final byte [] value) {
      float x = shortSignedAtOffset(value, 0) * (500f / 65536f);
      return new float[]{x};
    }
  },
  GYROSCOPE_Y(UUID_GYR_SERV, UUID_GYR_DATA, UUID_GYR_CONF, UUID_GYR_PERI, (byte)2) {
    @Override
    public float[] convert(final byte [] value) {
      float y = shortSignedAtOffset(value, 0) * (500f / 65536f) * -1;
      return new float[]{y};
    }
  },
  GYROSCOPE_XY(UUID_GYR_SERV, UUID_GYR_DATA, UUID_GYR_CONF, UUID_GYR_PERI, (byte)3) {
    @Override
    public float[] convert(final byte [] value) {
      float y = shortSignedAtOffset(value, 0) * (500f / 65536f) * -1;
      float x = shortSignedAtOffset(value, 2) * (500f / 65536f);
      return new float[]{x,y};
    }
  },
  GYROSCOPE_Z(UUID_GYR_SERV, UUID_GYR_DATA, UUID_GYR_CONF, UUID_GYR_PERI, (byte)4) {
    @Override
    public float[] convert(final byte [] value) {
      float z = shortSignedAtOffset(value, 0) * (500f / 65536f);
      return new float[]{z};
    }
  },
  GYROSCOPE_XZ(UUID_GYR_SERV, UUID_GYR_DATA, UUID_GYR_CONF, UUID_GYR_PERI, (byte)5) {
    @Override
    public float[] convert(final byte [] value) {
      float x = shortSignedAtOffset(value, 0) * (500f / 65536f);
      float z = shortSignedAtOffset(value, 2) * (500f / 65536f);
      return new float[]{x,z};
    }
  },
  GYROSCOPE_YZ(UUID_GYR_SERV, UUID_GYR_DATA, UUID_GYR_CONF, UUID_GYR_PERI, (byte)6) {
    @Override
    public float[] convert(final byte [] value) {
      float y = shortSignedAtOffset(value, 0) * (500f / 65536f) * -1;
      float z = shortSignedAtOffset(value, 2) * (500f / 65536f);
      return new float[]{y,z};
    }
  },

  BAROMETER(UUID_BAR_SERV, UUID_BAR_DATA, UUID_BAR_CONF, UUID_BAR_PERI) {
    @Override
    public float[] convert(final byte [] value) {
        if (barometerCalibrationCoefficients == null) {
            // Log.w("Sensor", "Data notification arrived for barometer before it was calibrated.");
            return null;
        }

        final int[] c; // Calibration coefficients
        final Integer t_r; // Temperature raw value from sensor
        final Integer p_r; // Pressure raw value from sensor
        final Double S; // Interim value in calculation
        final Double O; // Interim value in calculation
        final Double p_a; // Pressure actual value in unit Pascal.

        c = new int[barometerCalibrationCoefficients.size()];
        for (int i = 0; i < barometerCalibrationCoefficients.size(); i++) {
            c[i] = barometerCalibrationCoefficients.get(i);
        }

        t_r = shortSignedAtOffset(value, 0);
        p_r = shortUnsignedAtOffset(value, 2);

        S = c[2] + c[3] * t_r / pow(2, 17) + ((c[4] * t_r / pow(2, 15)) * t_r) / pow(2, 19);
        O = c[5] * pow(2, 14) + c[6] * t_r / pow(2, 3) + ((c[7] * t_r / pow(2, 15)) * t_r) / pow(2, 4);
        p_a = (S * p_r + O) / pow(2, 14);

        return new float[]{p_a.floatValue()};
    }
  },

  SIMPLE_KEYS(UUID_KEY_SERV, UUID_KEY_DATA, null, null) {
  };

  /**
   * Gyroscope, Magnetometer, Barometer, IR temperature all store 16 bit two's complement values as LSB MSB, which cannot be directly parsed
   * as getIntValue(FORMAT_SINT16, offset) because the bytes are stored as little-endian.
   * 
   * This function extracts these 16 bit two's complement values.
   * */
  private static Integer shortSignedAtOffset(byte[] c, int offset) {
    Integer lowerByte = (int) c[offset] & 0xFF; 
    Integer upperByte = (int) c[offset+1]; // // Interpret MSB as signed
    return (upperByte << 8) + lowerByte;
  }

  private static Integer shortUnsignedAtOffset(byte[] c, int offset) {
    Integer lowerByte = (int) c[offset] & 0xFF; 
    Integer upperByte = (int) c[offset+1] & 0xFF; // // Interpret MSB as signed
    return (upperByte << 8) + lowerByte;
  }

  public float[] convert(byte[] value) {
    throw new UnsupportedOperationException("Error: the individual enum classes are supposed to override this method.");
  }
    public static List<Integer> barometerCalibrationCoefficients;
    public static double heightCalibration;
	private final UUID service, data, config, period;
	private byte enableCode; // See getEnableSensorCode for explanation.
	public static final byte DISABLE_SENSOR_CODE = 0;
	public static final byte ENABLE_SENSOR_CODE = 1;
	public static final byte CALIBRATE_SENSOR_CODE = 2;

	/**
	 * Constructor called by the Gyroscope and Accelerometer because it more than a boolean enable
	 * code.
	 */
  private Sensor(UUID service, UUID data, UUID config, UUID period, byte enableCode) {
    this.service = service;
    this.data = data;
    this.config = config;
    this.period = period;
    this.enableCode = enableCode;
  }

  /**
   * Constructor called by all the sensors except Gyroscope
   * */
  private Sensor(UUID service, UUID data, UUID config, UUID period) {
    this.service = service;
    this.data = data;
    this.config = config;
    this.period = period;
    this.enableCode = ENABLE_SENSOR_CODE; // This is the sensor enable code for all sensors except the gyroscope
  }

  /**
   * @return the code which, when written to the configuration characteristic, turns on the sensor.
   * */
  public byte getEnableSensorCode() {
    return enableCode;
  }

  public UUID getService() {
    return service;
  }

  public UUID getData() {
    return data;
  }

  public UUID getConfig() {
    return config;
  }

  public UUID getPeriod() {return period; }

  public static Sensor getFromDataUuid(UUID uuid) {
    for (Sensor s : Sensor.values()) {
      if (s.getData().equals(uuid)) {
        return s;
      }
    }
    throw new RuntimeException("unable to find UUID.");
  }
  
  public static final Sensor[] SENSOR_LIST = {IR_TEMPERATURE, ACCELEROMETER, MAGNETOMETER, LUXOMETER, GYROSCOPE, HUMIDITY, BAROMETER, SIMPLE_KEYS};
}
