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

#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include "a3d/a3d_texfont.h"
#include "loax/loax_server.h"

#define LOG_TAG "loax"
#include "a3d/a3d_log.h"

/***********************************************************
* private                                                  *
***********************************************************/

typedef struct
{
	loax_server_t* server;
	a3d_texfont_t* font;
} loax_renderer_t;

loax_renderer_t* loax_renderer_new(const char* font)
{
	assert(font);
	LOGD("debug font=%s", font);

	loax_renderer_t* self = (loax_renderer_t*) malloc(sizeof(loax_renderer_t));
	if(self == NULL)
	{
		LOGE("malloc failed");
		return NULL;
	}

	self->font = a3d_texfont_new(font);
	if(self->font == NULL)
	{
		goto fail_font;
	}

	self->server = loax_server_new();
	if(self->server == NULL)
	{
		goto fail_server;
	}

	// success
	return self;

	// failure
	fail_server:
		a3d_texfont_delete(&self->font);
	fail_font:
		free(self);
	return NULL;
}

void loax_renderer_delete(loax_renderer_t** _self)
{
	assert(_self);

	loax_renderer_t* self = *_self;
	if(self)
	{
		LOGD("debug");
		a3d_texfont_delete(&self->font);
		loax_server_delete(&self->server);
		free(self);
		*_self = NULL;
	}
}

void loax_renderer_resize(loax_renderer_t* self, int w, int h)
{
	assert(self);
	LOGD("debug w=%i, h=%i", w, h);

	if(self)
	{
		loax_server_resize(self->server, w, h);
	}
}

void loax_renderer_draw(loax_renderer_t* self)
{
	assert(self);
	LOGD("debug");
	loax_server_draw(self->server);
}

static loax_renderer_t* loax_renderer = NULL;

/***********************************************************
* public                                                   *
***********************************************************/

JNIEXPORT void JNICALL Java_com_jeffboody_a3d_A3DNativeRenderer_NativeCreate(JNIEnv* env)
{
	assert(env);
	LOGD("debug");

	if(loax_renderer != NULL)
	{
		LOGE("renderer already exists");
		return;
	}

	if(a3d_GL_load() == 0)
	{
		LOGE("a3d_GL_load failed");
		return;
	}

	loax_renderer = loax_renderer_new("/data/data/com.jeffboody.LOAXServer/files/whitrabt.tex.gz");
	if(loax_renderer == NULL)
	{
		a3d_GL_unload();
	}
}

JNIEXPORT void JNICALL Java_com_jeffboody_a3d_A3DNativeRenderer_NativeDestroy(JNIEnv* env)
{
	assert(env);
	LOGD("debug");

	if(loax_renderer)
	{
		loax_renderer_delete(&loax_renderer);
		a3d_GL_unload();
	}
}

JNIEXPORT void JNICALL Java_com_jeffboody_a3d_A3DNativeRenderer_NativeChangeSurface(JNIEnv* env, jobject  obj, jint w, jint h)
{
	assert(env);
	LOGD("debug");

	if(loax_renderer)
	{
		loax_renderer_resize(loax_renderer, w, h);
	}
}

JNIEXPORT void JNICALL Java_com_jeffboody_a3d_A3DNativeRenderer_NativeDraw(JNIEnv* env)
{
	assert(env);
	LOGD("debug");

	if(loax_renderer)
	{
		a3d_GL_frame_begin();
		loax_renderer_draw(loax_renderer);
		a3d_GL_frame_end();
	}
}

JNIEXPORT int JNICALL Java_com_jeffboody_a3d_A3DNativeRenderer_NativeClientVersion(JNIEnv* env)
{
	assert(env);
	LOGD("debug");
	return 2;
}

JNIEXPORT void JNICALL Java_com_jeffboody_LOAXServer_LOAXServer_NativeKeyDown(JNIEnv* env, jobject  obj, jint keycode, jint meta)
{
	assert(env);
	LOGD("debug keycode=0x%X, meta=0x%X", keycode, meta);

	if(loax_renderer)
	{
		loax_server_keydown(loax_renderer->server, keycode, meta);
	}
}

JNIEXPORT void JNICALL Java_com_jeffboody_LOAXServer_LOAXServer_NativeKeyUp(JNIEnv* env, jobject  obj, jint keycode, jint meta)
{
	assert(env);
	LOGD("debug keycode=0x%X, meta=0x%X", keycode, meta);

	if(loax_renderer)
	{
		loax_server_keyup(loax_renderer->server, keycode, meta);
	}
}

JNIEXPORT void JNICALL Java_com_jeffboody_LOAXServer_LOAXServer_NativeButtonDown(JNIEnv* env, jobject  obj, jint keycode)
{
	assert(env);
	LOGD("debug keycode=0x%X", keycode);

	if(loax_renderer)
	{
		loax_server_buttondown(loax_renderer->server, keycode);
	}
}

JNIEXPORT void JNICALL Java_com_jeffboody_LOAXServer_LOAXServer_NativeButtonUp(JNIEnv* env, jobject  obj, jint keycode)
{
	assert(env);
	LOGD("debug keycode=0x%X", keycode);

	if(loax_renderer)
	{
		loax_server_buttonup(loax_renderer->server, keycode);
	}
}

JNIEXPORT void JNICALL Java_com_jeffboody_LOAXServer_LOAXServer_NativeTouch(JNIEnv* env, jobject  obj, jint action, jint count,
                                                                            jfloat x0, jfloat y0,
                                                                            jfloat x1, jfloat y1,
                                                                            jfloat x2, jfloat y2,
                                                                            jfloat x3, jfloat y3)
{
	assert(env);
	LOGD("debug action=0x%X, count=%i", action, count);

	if(loax_renderer)
	{
		// error handling is in loax_server_touch
		float coord[8] = { x0, y0, x1, y1, x2, y2, x3, y3 };
		loax_server_touch(loax_renderer->server, action, count, coord);
	}
}
