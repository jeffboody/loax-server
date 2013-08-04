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
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.InputDevice;
import android.view.Window;
import android.view.WindowManager;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import com.jeffboody.a3d.A3DSurfaceView;
import com.jeffboody.a3d.A3DNativeRenderer;
import com.jeffboody.a3d.A3DResource;

public class LOAXServer extends Activity implements SensorEventListener
{
	private static final String TAG = "LOAXServer";

	private A3DNativeRenderer Renderer;
	private A3DSurfaceView    Surface;

	// axis values
	private float AX1 = 0.0F;
	private float AY1 = 0.0F;
	private float AX2 = 0.0F;
	private float AY2 = 0.0F;

	// sensors
	private Sensor  mAccelerometer;
	private Sensor  mMagnetic;
	private float[] mAccelerometerValues = new float[3];
	private float[] mMagneticValues      = new float[3];
	private boolean mAccelerometerReady  = false;
	private boolean mMagneticReady       = false;

	/*
	 * Native interface
	 */

	private native void NativeKeyDown(int keycode, int meta);
	private native void NativeKeyUp(int keycode, int meta);
	private native void NativeButtonDown(int id, int keycode);
	private native void NativeButtonUp(int id, int keycode);
	private native void NativeAxisMove(int id, int axis, float value);
	private native void NativeTouch(int action, int count,
	                                float x0, float y0,
	                                float x1, float y1,
	                                float x2, float y2,
	                                float x3, float y3);
	private native void NativeOrientation(float ax, float ay, float az,
	                                      float mx, float my, float mz,
	                                      int   rotation);

	// "singleton" used for callbacks
	private static LOAXServer mSelf = null;

	// lock callback functions since they happen on rendering thread
	private Lock mNativeLock = new ReentrantLock();

	private static final int LOAX_CMD_ORIENTATION_ENABLE  = 0x00010000;
	private static final int LOAX_CMD_ORIENTATION_DISABLE = 0x00010001;

	private static int CallbackCmd(int cmd)
	{
		if(mSelf == null)
		{
			// ignore
		}
		else if(cmd == LOAX_CMD_ORIENTATION_ENABLE)
		{
			return mSelf.sensorOrientationEnable();
		}
		else if(cmd == LOAX_CMD_ORIENTATION_DISABLE)
		{
			return mSelf.sensorOrientationDisable();
		}
		return 0;
	}

	/*
	 * Activity interface
	 */

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		mSelf = this;

		A3DResource r = new A3DResource(this, R.raw.timestamp);
		r.Add(R.raw.whitrabt, "whitrabt.tex.gz");

		// Make window fullscreen
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
		                     WindowManager.LayoutParams.FLAG_FULLSCREEN);

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
		sensorOrientationDisable();
		Surface.PauseRenderer();
		super.onPause();
	}

	@Override
	protected void onDestroy()
	{
		Surface.StopRenderer();
		Surface = null;
		Renderer = null;
		mSelf = null;
		super.onDestroy();
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

	@Override
	public boolean onKeyDown(int keycode, KeyEvent event)
	{
		int ascii = event.getUnicodeChar(0);
		int meta  = event.getMetaState();
		if((ascii > 0) && (ascii < 128))
		{
			NativeKeyDown(ascii, meta);
		}
		else if(isGameKey(keycode))
		{
			NativeButtonDown(event.getDeviceId(), keycode);
		}
		return true;
	}

	@Override
	public boolean onKeyUp(int keycode, KeyEvent event)
	{
		int ascii = event.getUnicodeChar(0);
		int meta  = event.getMetaState();
		if((ascii > 0) && (ascii < 128))
		{
			NativeKeyUp(ascii, meta);
		}
		else if(isGameKey(keycode))
		{
			NativeButtonUp(event.getDeviceId(), keycode);
		}
		return true;
	}

	@Override
	public boolean onGenericMotionEvent(MotionEvent event)
	{
		int source = event.getSource();
		int action = event.getAction();
		int id     = event.getDeviceId();
		if((source & InputDevice.SOURCE_CLASS_JOYSTICK) != 0)
		{
			if(action == MotionEvent.ACTION_MOVE)
			{
				// process the joystick movement...
				float ax1 = event.getAxisValue(MotionEvent.AXIS_X);
				float ay1 = event.getAxisValue(MotionEvent.AXIS_Y);
				float ax2 = event.getAxisValue(MotionEvent.AXIS_Z);
				float ay2 = event.getAxisValue(MotionEvent.AXIS_RZ);

				if(ax1 != AX1)
				{
					NativeAxisMove(id, MotionEvent.AXIS_X, ax1);
					AX1 = ax1;
				}
				if(ay1 != AY1)
				{
					NativeAxisMove(id, MotionEvent.AXIS_Y,  ay1);
					AY1 = ay1;
				}
				if(ax2 != AX2)
				{
					NativeAxisMove(id, MotionEvent.AXIS_Z,  ax2);
					AX2 = ax2;
				}
				if(ay2 != AY2)
				{
					NativeAxisMove(id, MotionEvent.AXIS_RZ, ay2);
					AY2 = ay2;
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
			int action = event.getAction();
			int count  = event.getPointerCount();

			if(count == 1)
			{
				NativeTouch(action, count,
				            event.getX(), event.getY(),
				            0.0f, 0.0f,
				            0.0f, 0.0f,
				            0.0f, 0.0f);
			}
			else if(count == 2)
			{
				NativeTouch(action, count,
				            event.getX(event.findPointerIndex(0)),
				            event.getY(event.findPointerIndex(0)),
				            event.getX(event.findPointerIndex(1)),
				            event.getY(event.findPointerIndex(1)),
				            0.0f, 0.0f,
				            0.0f, 0.0f);
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
				            0.0f, 0.0f);
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
				            event.getY(event.findPointerIndex(3)));
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
	 * SensorEventListener interface
	 */

	private int sensorOrientationEnable()
	{

		mNativeLock.lock();
		try
		{
			SensorManager sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

			if(mMagnetic == null)
			{
				mMagnetic = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
				if(mMagnetic != null)
				{
					sm.registerListener(this,
					                    mMagnetic,
					                    SensorManager.SENSOR_DELAY_GAME);
				}
			}

			if(mAccelerometer == null)
			{
				mAccelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
				if(mAccelerometer != null)
				{
					sm.registerListener(this,
					                    mAccelerometer,
					                    SensorManager.SENSOR_DELAY_GAME);
				}
			}

			if((mMagnetic == null) || (mAccelerometer == null))
			{
				sensorOrientationDisable();
				return 0;
			}

			return 1;
		}
		finally
		{
			mNativeLock.unlock();
		}
	}

	private int sensorOrientationDisable()
	{
		mNativeLock.lock();
		try
		{
			SensorManager sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
			if(mAccelerometer != null)
			{
				sm.unregisterListener(this, mAccelerometer);
				mAccelerometer = null;
			}
			if(mMagnetic != null)
			{
				sm.unregisterListener(this, mMagnetic);
				mMagnetic = null;
			}

			mAccelerometerReady = false;
			mMagneticReady      = false;

			return 1;
		}
		finally
		{
			mNativeLock.unlock();
		}
	}

	public void onSensorChanged(SensorEvent event)
	{
		boolean update_orientation = false;

		if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
		{
			mAccelerometerValues[0] = event.values[0];
			mAccelerometerValues[1] = event.values[1];
			mAccelerometerValues[2] = event.values[2];
			mAccelerometerReady     = true;
			update_orientation      = true;
		}
		else if(event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
		{
			mMagneticValues[0] = event.values[0];
			mMagneticValues[1] = event.values[1];
			mMagneticValues[2] = event.values[2];
			mMagneticReady     = true;
			update_orientation = true;
		}

		if(update_orientation && mAccelerometerReady && mMagneticReady)
		{
			int   rotation = 90*getWindowManager().getDefaultDisplay().getRotation();
			float ax       = mAccelerometerValues[0];
			float ay       = mAccelerometerValues[1];
			float az       = mAccelerometerValues[2];
			float mx       = mMagneticValues[0];
			float my       = mMagneticValues[1];
			float mz       = mMagneticValues[2];
			NativeOrientation(ax, ay, az,
			                  mx, my, mz,
			                  rotation);
		}
	}

	public void onAccuracyChanged(Sensor sensor, int accuracy)
	{
	}

	static
	{
		System.loadLibrary("net");
		System.loadLibrary("a3d");
		System.loadLibrary("loax");
		System.loadLibrary("LOAXServer");
	}
}
