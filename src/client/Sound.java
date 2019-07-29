package client;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;

public class Sound {
	Clip clip;
	void loadSound(File file) {
		try {
			AudioInputStream in = AudioSystem.getAudioInputStream(file);
			clip = AudioSystem.getClip();
			clip.open(in);
			}catch (IOException e) {
			e.printStackTrace();
			}catch (LineUnavailableException e) {
			e.printStackTrace();
			}catch (UnsupportedAudioFileException e) {
			e.printStackTrace();
		}		
	}
	public void rewindStart() {
		
		clip.setFramePosition(0);
		clip.start();
	}
	void rewindStop () {
		
		if (clip.isRunning())
			clip.stop();
			clip.setFramePosition(0);
	}
}
