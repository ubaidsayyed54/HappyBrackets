package net.happybrackets.tutorial.session2;

import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import net.beadsproject.beads.core.AudioContext;
import net.beadsproject.beads.core.Bead;
import net.beadsproject.beads.data.Buffer;
import net.beadsproject.beads.data.Pitch;
import net.beadsproject.beads.events.KillTrigger;
import net.beadsproject.beads.ugens.*;
import net.happybrackets.assignment_tasks.WaveformVisualiser;

import java.util.Random;

/**
 * Created by ollie on 25/07/2016.
 */
public class Example2_2 extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        //set up the audio context
        AudioContext ac = new AudioContext();
        ac.start();

        //random number generator
        Random random = new Random();

        Envelope e = new Envelope(ac, 500);
        e.addSegment(1000, 5000);

        //the clock
        Clock clock = new Clock(ac, e);
        ac.out.addDependent(clock);

        //the base pitch
        int basePitch = 50;

        //delay
        TapIn tin = new TapIn(ac, 10000);
        TapOut tout = new TapOut(ac, tin, e);
        Gain delayFeedbackGain = new Gain(ac, 1, 0.7f);
        delayFeedbackGain.addInput(tout);
        tin.addInput(delayFeedbackGain);
        ac.out.addInput(delayFeedbackGain);

        //filter
        BiquadFilter filter = new BiquadFilter(ac, 2, BiquadFilter.Type.LP);
        filter.setFrequency(100f);

        //connect filter to delay
        tin.addInput(filter);
        ac.out.addInput(filter);

        clock.addMessageListener(new Bead() {
            @Override
            protected void messageReceived(Bead bead) {

                if(clock.getCount() % 32 == 0) {
                    //add the waveplayer
                    int pitch = basePitch + 12 + Pitch.major[random.nextInt(7)];
                    float freq = Pitch.mtof(pitch);
                    WavePlayer wp = new WavePlayer(ac, freq, Buffer.SQUARE);
                    //add the gain
                    Envelope e = new Envelope(ac, 0.1f);
                    Gain g = new Gain(ac, 1, e);
                    e.addSegment(0, 200, new KillTrigger(g));
                    //connect together
                    g.addInput(wp);
                    filter.addInput(g);
                    filter.setFrequency(freq * 4);
                }

            }
        });

        //visualiser
        WaveformVisualiser.open(ac);
    }

}
