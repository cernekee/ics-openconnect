package de.blinkt.openvpn;

import android.os.Handler;
import android.os.Looper;

public abstract class UiTask implements Runnable {
	public abstract Object fn(Object arg);

	private Object arg;
	private Object result;
	private boolean done = false;
	private boolean completeOnReturn = true;
	private Object lock = new Object();

	@Override
	public void run() {
		synchronized (lock) {
			Object localResult = fn(arg);
			if (completeOnReturn) {
				complete(localResult);
			}
		}
	}

	protected void holdoff() {
		completeOnReturn = false;
	}

	protected void complete(Object result) {
		synchronized (lock) {
			done = true;
			this.result = result;
			lock.notifyAll();
		}
	}

	public Object go(Object arg) {
		Handler h = new Handler(Looper.getMainLooper());

		this.arg = arg;
		h.post(this);
		synchronized (lock) {
			while (!done) {
				try {
					lock.wait();
				} catch (InterruptedException e) {
				}
			}
		}
		return result;
	}
}