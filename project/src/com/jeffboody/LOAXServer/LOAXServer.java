/*
 * Copyright (c) 2013 Jeff Boody
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 */

package com.jeffboody.LOAXServer;

import android.util.Log;
import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.hardware.SensorManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.location.LocationManager;
import android.location.Location;
import android.location.LocationListener;
import android.os.Message;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.InputDevice;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import java.lang.Math;
import com.jeffboody.a3d.A3DSurfaceView;
import com.jeffboody.a3d.A3DNativeRenderer;
import com.jeffboody.a3d.A3DResource;

public class LOAXServer extends Activity implements SensorEventListener, LocationListener, Handler.Callback
{
	private static final String TAG = "LOAXServer";

	private A3DNativeRenderer Renderer;
	private A3DSurfaceView    Surface;

	// axis values
	private float AX1 = 0.0F;
	private float AY1 = 0.0F;
	private float AX2 = 0.0F;
	private float AY2 = 0.0F;
	private float AHX = 0.0F;
	private float AHY = 0.0F;

	// sensors
	private Sensor  mAccelerometer;
	private Sensor  mMagnetic;
	private Sensor  mGyroscope;

	// "singleton" used for callbacks
	// handler is used to trigger events on UI thread
	private static Handler mHandler = null;

	/*
	 * Native interface
	 */

	private native void NativeKeyDown(int keycode, int meta, double utime);
	private native void NativeKeyUp(int keycode, int meta, double utime);
	private native void NativeButtonDown(int id, int keycode, double utime);
	private native void NativeButtonUp(int id, int keycode, double utime);
	private native void NativeAxisMove(int id, int axis,
	                                   float value, double utime);
	private native void NativeTouch(int action, int count,
	                                float x0, float y0,
	                                float x1, float y1,
	                                float x2, float y2,
	                                float x3, float y3,
	                                double utime);
	private native void NativeAccelerometer(float ax, float ay,
	                                        float az, int rotation,
	                                        double utime);
	private native void NativeGyroscope(float ax, float ay, float az,
	                                    double utime);
	private native void NativeMagnetometer(float mx, float my,
	                                       float mz, double utime);
	private native void NativeGps(double lat, double lon,
	                              float accuracy, float altitude,
	                              float speed, float bearing,
	                              double utime);

	private static final int LOAX_CMD_ACCELEROMETER_ENABLE  = 0x00010000;
	private static final int LOAX_CMD_ACCELEROMETER_DISABLE = 0x00010001;
	private static final int LOAX_CMD_MAGNETOMETER_ENABLE   = 0x00010002;
	private static final int LOAX_CMD_MAGNETOMETER_DISABLE  = 0x00010003;
	private static final int LOAX_CMD_GPS_ENABLE            = 0x00010004;
	private static final int LOAX_CMD_GPS_DISABLE           = 0x00010005;
	private static final int LOAX_CMD_GYROSCOPE_ENABLE      = 0x00010006;
	private static final int LOAX_CMD_GYROSCOPE_DISABLE     = 0x00010007;
	private static final int LOAX_CMD_KEEPSCREENON_ENABLE   = 0x00010008;
	private static final int LOAX_CMD_KEEPSCREENON_DISABLE  = 0x00010009;

	private static final int LOAX_KEY_ENTER     = 0x00A;
	private static final int LOAX_KEY_ESCAPE    = 0x01B;
	private static final int LOAX_KEY_BACKSPACE = 0x008;
	private static final int LOAX_KEY_DELETE    = 0x07F;
	private static final int LOAX_KEY_UP        = 0x100;
	private static final int LOAX_KEY_DOWN      = 0x101;
	private static final int LOAX_KEY_LEFT      = 0x102;
	private static final int LOAX_KEY_RIGHT     = 0x103;
	private static final int LOAX_KEY_HOME      = 0x104;
	private static final int LOAX_KEY_END       = 0x105;
	private static final int LOAX_KEY_PGUP      = 0x106;
	private static final int LOAX_KEY_PGDOWN    = 0x107;
	private static final int LOAX_KEY_INSERT    = 0x108;

	private static double getUtime(double t0)
	{
		// convert "uptime" timestamp to UTC/us timestamp
		double now = (double) System.currentTimeMillis();
		double t1  = (double) SystemClock.uptimeMillis();
		return 1000.0*(now + t0 - t1);
	}

	private static void CallbackCmd(int cmd)
	{
		try
		{
			mHandler.sendMessage(Message.obtain(mHandler, cmd));
		}
		catch(Exception e)
		{
			// ignore
		}
	}

	/*
	 * Activity interface
	 */

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		mHandler = new Handler(this);

		A3DResource r = new A3DResource(this, R.raw.timestamp);
		r.Add(R.raw.whitrabt, "whitrabt.tex.gz");

		Renderer = new A3DNativeRenderer(this);
		Surface  = new A3DSurfaceView(Renderer, r, this);
		setContentView(Surface);
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		Surface.ResumeRenderer();
	}

	@Override
	protected void onPause()
	{
		cmdKeepScreenOnDisable();
		Surface.PauseRenderer();
		cmdAccelerometerDisable();
		cmdMagnetometerDisable();
		cmdGpsDisable();
		cmdGyroscopeDisable();
		super.onPause();
	}

	@Override
	protected void onDestroy()
	{
		Surface.StopRenderer();
		Surface  = null;
		Renderer = null;
		mHandler = null;
		super.onDestroy();
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus)
	{
		super.onWindowFocusChanged(hasFocus);
		if(hasFocus)
		{
			Surface.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE          |
			                              View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
			                              View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN      |
			                              View.SYSTEM_UI_FLAG_HIDE_NAVIGATION        |
			                              View.SYSTEM_UI_FLAG_FULLSCREEN             |
			                              View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
		}
	}

	private static boolean isGameKey(int keycode)
	{
		if((keycode == KeyEvent.KEYCODE_DPAD_CENTER) ||
		   (keycode == KeyEvent.KEYCODE_DPAD_UP)     ||
		   (keycode == KeyEvent.KEYCODE_DPAD_DOWN)   ||
		   (keycode == KeyEvent.KEYCODE_DPAD_LEFT)   ||
		   (keycode == KeyEvent.KEYCODE_DPAD_RIGHT))
		{
			return true;
		}

		return KeyEvent.isGamepadButton(keycode);
	}

	private static int getLoaxKey(int keycode, KeyEvent event)
	{
		int ascii = event.getUnicodeChar(0);
		if(keycode == KeyEvent.KEYCODE_ENTER)
		{
			return LOAX_KEY_ENTER;
		}
		else if(keycode == KeyEvent.KEYCODE_ESCAPE)
		{
			return LOAX_KEY_ESCAPE;
		}
		else if(keycode == KeyEvent.KEYCODE_DEL)
		{
			return LOAX_KEY_BACKSPACE;
		}
		else if(keycode == KeyEvent.KEYCODE_FORWARD_DEL)
		{
			return LOAX_KEY_DELETE;
		}
		else if(keycode == KeyEvent.KEYCODE_DPAD_UP)
		{
			return LOAX_KEY_UP;
		}
		else if(keycode == KeyEvent.KEYCODE_DPAD_DOWN)
		{
			return LOAX_KEY_DOWN;
		}
		else if(keycode == KeyEvent.KEYCODE_DPAD_LEFT)
		{
			return LOAX_KEY_LEFT;
		}
		else if(keycode == KeyEvent.KEYCODE_DPAD_RIGHT)
		{
			return LOAX_KEY_RIGHT;
		}
		else if(keycode == KeyEvent.KEYCODE_MOVE_HOME)
		{
			return LOAX_KEY_HOME;
		}
		else if(keycode == KeyEvent.KEYCODE_MOVE_END)
		{
			return LOAX_KEY_END;
		}
		else if(keycode == KeyEvent.KEYCODE_PAGE_UP)
		{
			return LOAX_KEY_PGUP;
		}
		else if(keycode == KeyEvent.KEYCODE_PAGE_DOWN)
		{
			return LOAX_KEY_PGDOWN;
		}
		else if((ascii > 0) && (ascii < 128))
		{
			return ascii;
		}
		return 0;
	}

	private static float denoiseAxis(float value)
	{
		if(Math.abs(value) < 0.05F)
		{
			return 0.0F;
		}
		return value;
	}

	@Override
	public boolean onKeyDown(int keycode, KeyEvent event)
	{
		int    ascii = getLoaxKey(keycode, event);
		int    meta  = event.getMetaState();
		double utime = getUtime(event.getEventTime());

		if(ascii > 0)
		{
			NativeKeyDown(ascii, meta, utime);
		}

		if(isGameKey(keycode))
		{
			NativeButtonDown(event.getDeviceId(), keycode, utime);
		}

		return true;
	}

	@Override
	public boolean onKeyUp(int keycode, KeyEvent event)
	{
		int    ascii = getLoaxKey(keycode, event);
		int    meta  = event.getMetaState();
		double utime = getUtime(event.getEventTime());

		if(ascii > 0)
		{
			NativeKeyUp(ascii, meta, utime);
		}

		if(isGameKey(keycode))
		{
			NativeButtonUp(event.getDeviceId(), keycode, utime);
		}

		return true;
	}

	@Override
	public boolean onGenericMotionEvent(MotionEvent event)
	{
		int    source = event.getSource();
		int    action = event.getAction();
		int    id     = event.getDeviceId();
		double utime  = getUtime(event.getEventTime());
		if((source & InputDevice.SOURCE_CLASS_JOYSTICK) != 0)
		{
			if(action == MotionEvent.ACTION_MOVE)
			{
				// process the joystick movement...
				float ax1 = denoiseAxis(event.getAxisValue(MotionEvent.AXIS_X));
				float ay1 = denoiseAxis(event.getAxisValue(MotionEvent.AXIS_Y));
				float ax2 = denoiseAxis(event.getAxisValue(MotionEvent.AXIS_Z));
				float ay2 = denoiseAxis(event.getAxisValue(MotionEvent.AXIS_RZ));
				float ahx = denoiseAxis(event.getAxisValue(MotionEvent.AXIS_HAT_X));
				float ahy = denoiseAxis(event.getAxisValue(MotionEvent.AXIS_HAT_Y));

				if(ax1 != AX1)
				{
					NativeAxisMove(id, MotionEvent.AXIS_X, ax1, utime);
					AX1 = ax1;
				}
				if(ay1 != AY1)
				{
					NativeAxisMove(id, MotionEvent.AXIS_Y, ay1, utime);
					AY1 = ay1;
				}
				if(ax2 != AX2)
				{
					NativeAxisMove(id, MotionEvent.AXIS_Z, ax2, utime);
					AX2 = ax2;
				}
				if(ay2 != AY2)
				{
					NativeAxisMove(id, MotionEvent.AXIS_RZ, ay2, utime);
					AY2 = ay2;
				}
				if(ahx != AHX)
				{
					NativeAxisMove(id, MotionEvent.AXIS_HAT_X, ahx, utime);
					AHX = ahx;
				}
				if(ahy != AHY)
				{
					NativeAxisMove(id, MotionEvent.AXIS_HAT_Y, ahy, utime);
					AHY = ahy;
				}
				return true;
			}
		}
		return super.onGenericMotionEvent(event);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event)
	{
		try
		{
			int    action = event.getAction();
			int    count  = event.getPointerCount();
			double utime  = getUtime(event.getEventTime());
			if(count == 1)
			{
				NativeTouch(action, count,
				            event.getX(), event.getY(),
				            0.0f, 0.0f,
				            0.0f, 0.0f,
				            0.0f, 0.0f, utime);
			}
			else if(count == 2)
			{
				NativeTouch(action, count,
				            event.getX(event.findPointerIndex(0)),
				            event.getY(event.findPointerIndex(0)),
				            event.getX(event.findPointerIndex(1)),
				            event.getY(event.findPointerIndex(1)),
				            0.0f, 0.0f,
				            0.0f, 0.0f, utime);
			}
			else if(count == 3)
			{
				NativeTouch(action, count,
				            event.getX(event.findPointerIndex(0)),
				            event.getY(event.findPointerIndex(0)),
				            event.getX(event.findPointerIndex(1)),
				            event.getY(event.findPointerIndex(1)),
				            event.getX(event.findPointerIndex(2)),
				            event.getY(event.findPointerIndex(2)),
				            0.0f, 0.0f, utime);
			}
			else if(count >= 4)
			{
				NativeTouch(action, count,
				            event.getX(event.findPointerIndex(0)),
				            event.getY(event.findPointerIndex(0)),
				            event.getX(event.findPointerIndex(1)),
				            event.getY(event.findPointerIndex(1)),
				            event.getX(event.findPointerIndex(2)),
				            event.getY(event.findPointerIndex(2)),
				            event.getX(event.findPointerIndex(3)),
				            event.getY(event.findPointerIndex(3)),
				            utime);
			}
			else
			{
				return false;
			}
		}
		catch(Exception e)
		{
			// fail silently
			return false;
		}

		return true;
	}

	/*
	 * KeepScreenOn interface
	 */

	private void cmdKeepScreenOnEnable()
	{
		try
		{
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}
		catch(Exception e)
		{
			Log.e(TAG, "exception: " + e);
		}
	}

	private void cmdKeepScreenOnDisable()
	{
		try
		{
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}
		catch(Exception e)
		{
			Log.e(TAG, "exception: " + e);
		}
	}

	/*
	 * SensorEventListener interface
	 */

	private void cmdAccelerometerEnable()
	{
		if(mAccelerometer == null)
		{
			SensorManager sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
			mAccelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
			if(mAccelerometer != null)
			{
				sm.registerListener(this,
				                    mAccelerometer,
				                    SensorManager.SENSOR_DELAY_GAME);
			}
		}
	}

	private void cmdMagnetometerEnable()
	{
		if(mMagnetic == null)
		{
			SensorManager sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
			mMagnetic = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
			if(mMagnetic != null)
			{
				sm.registerListener(this,
				                    mMagnetic,
				                    SensorManager.SENSOR_DELAY_GAME);
			}
		}
	}

	private void cmdGyroscopeEnable()
	{
		if(mGyroscope == null)
		{
			SensorManager sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
			mGyroscope = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
			if(mGyroscope != null)
			{
				sm.registerListener(this,
				                    mGyroscope,
				                    SensorManager.SENSOR_DELAY_GAME);
			}
		}
	}

	private void cmdAccelerometerDisable()
	{
		if(mAccelerometer != null)
		{
			SensorManager sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
			sm.unregisterListener(this, mAccelerometer);
			mAccelerometer = null;
		}
	}

	private void cmdMagnetometerDisable()
	{
		if(mMagnetic != null)
		{
			SensorManager sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
			sm.unregisterListener(this, mMagnetic);
			mMagnetic = null;
		}
	}

	private void cmdGyroscopeDisable()
	{
		if(mGyroscope != null)
		{
			SensorManager sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
			sm.unregisterListener(this, mGyroscope);
			mGyroscope = null;
		}
	}

	public void onSensorChanged(SensorEvent event)
	{
		boolean update_orientation = false;
		double  utime              = (double) event.timestamp/1000.0;
		if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
		{
			int   r  = 90*getWindowManager().getDefaultDisplay().getRotation();
			float ax = event.values[0];
			float ay = event.values[1];
			float az = event.values[2];
			NativeAccelerometer(ax, ay, az, r, utime);
		}
		else if(event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
		{
			float mx = event.values[0];
			float my = event.values[1];
			float mz = event.values[2];
			NativeMagnetometer(mx, my, mz, utime);
		}
		else if(event.sensor.getType() == Sensor.TYPE_GYROSCOPE)
		{
			float ax = event.values[0];
			float ay = event.values[1];
			float az = event.values[2];
			NativeGyroscope(ax, ay, az, utime);
		}
	}

	public void onAccuracyChanged(Sensor sensor, int accuracy)
	{
	}

	/*
	 * LocationListener interface
	 */

	private void cmdGpsEnable()
	{
		LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

		// listen for gps
		try
		{
			if(lm.isProviderEnabled("gps") == false)
			{
				return;
			}

			lm.requestLocationUpdates("gps", 0L, 0.0F, this);
			onLocationChanged(lm.getLastKnownLocation("gps"));
		}
		catch(Exception e)
		{
			Log.e(TAG, "exception: " + e);
		}
	}

	private void cmdGpsDisable()
	{
		try
		{
			LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
			lm.removeUpdates(this);
		}
		catch(Exception e)
		{
			// ignore
		}
	}

	public void onLocationChanged(Location location)
	{
		double lat      = location.getLatitude();
		double lon      = location.getLongitude();
		float  accuracy = location.getAccuracy();
		float  altitude = (float) location.getAltitude();
		float  speed    = location.getSpeed();
		float  bearing  = location.getBearing();
		double utime    = 1000.0*(double) location.getTime();
		NativeGps(lat, lon, accuracy, altitude, speed, bearing, utime);
	}

	public void onProviderDisabled(String provider)
	{
	}

	public void onProviderEnabled(String provider)
	{
	}

	public void onStatusChanged(String provider, int status, Bundle extras)
	{
	}

	/*
	 * Handler.Callback interface
	 */

	public boolean handleMessage(Message msg)
	{
		int cmd = msg.what;
		if(cmd == LOAX_CMD_ACCELEROMETER_ENABLE)
		{
			cmdAccelerometerEnable();
		}
		else if(cmd == LOAX_CMD_ACCELEROMETER_DISABLE)
		{
			cmdAccelerometerDisable();
		}
		else if(cmd == LOAX_CMD_MAGNETOMETER_ENABLE)
		{
			cmdMagnetometerEnable();
		}
		else if(cmd == LOAX_CMD_MAGNETOMETER_DISABLE)
		{
			cmdMagnetometerDisable();
		}
		else if(cmd == LOAX_CMD_GPS_ENABLE)
		{
			cmdGpsEnable();
		}
		else if(cmd == LOAX_CMD_GPS_DISABLE)
		{
			cmdGpsDisable();
		}
		else if(cmd == LOAX_CMD_GYROSCOPE_ENABLE)
		{
			cmdGyroscopeEnable();
		}
		else if(cmd == LOAX_CMD_GYROSCOPE_DISABLE)
		{
			cmdGyroscopeDisable();
		}
		else if(cmd == LOAX_CMD_KEEPSCREENON_ENABLE)
		{
			cmdKeepScreenOnEnable();
		}
		else if(cmd == LOAX_CMD_KEEPSCREENON_DISABLE)
		{
			cmdKeepScreenOnDisable();
		}
		return true;
	}

	static
	{
		System.loadLibrary("net");
		System.loadLibrary("a3d");
		System.loadLibrary("loax");
		System.loadLibrary("LOAXServer");
	}
}
