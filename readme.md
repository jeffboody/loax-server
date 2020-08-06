About
=====

BETA release!!!

LOAX is a library for serializing GLES2 commands via TCP that can be rendered
on a LOAX server. This mechanism solves a fundamental limitation of
Linux-on-Android which does not support native graphics. This approach is not
without downsides as it does require more CPU overhead to transmit the GL
commands via TCP and some commands such as glReadPixels will be much slower.
The CPU overhead for Gears increased from ~12% to ~25% on the Nexus 10. The
LOAX server also forwards Android events to the LOAX client to support
various input devices.

Partially Supported:

* glShaderSource
* glVertexAttribPointer (VBOs are required)

Unsupported:

* glCompressedTexImage2D
* glCompressedTexSubImage2D
* glGetString
* glGetUniformfv
* glGetUniformiv
* glGetVertexAttribPointerv
* glShaderBinary

Events supported:

* resizes
* keyboard
* touchscreen
* joystick button
* joystick axis

Support for event handling is also planned for audio, camera and sensors.

Send questions or comments to Jeff Boody - jeffboody@gmail.com

License
=======

	Copyright (c) 2013 Jeff Boody

	Permission is hereby granted, free of charge, to any person obtaining a
	copy of this software and associated documentation files (the "Software"),
	to deal in the Software without restriction, including without limitation
	the rights to use, copy, modify, merge, publish, distribute, sublicense,
	and/or sell copies of the Software, and to permit persons to whom the
	Software is furnished to do so, subject to the following conditions:

	The above copyright notice and this permission notice shall be included
	in all copies or substantial portions of the Software.

	THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
	IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
	FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
	AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
	LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
	OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
	THE SOFTWARE.
