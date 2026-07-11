/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5.sound;

import javax.sound.sampled.Mixer;
import javax.sound.sampled.spi.MixerProvider;


/**
 * Service provider for {@link Html5Mixer}, registered in
 * {@code META-INF/services}. {@code Main} additionally points the
 * {@code javax.sound.sampled.SourceDataLine} / {@code .Clip} default-device
 * properties here so unmodified apps pick it up without configuration.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-11 nsano initial version <br>
 */
public class Html5MixerProvider extends MixerProvider {

    private static final Html5Mixer mixer = new Html5Mixer();

    @Override
    public Mixer.Info[] getMixerInfo() {
        return new Mixer.Info[] {Html5Mixer.info()};
    }

    @Override
    public Mixer getMixer(Mixer.Info info) {
        if (info == null || Html5Mixer.info().equals(info)) {
            return mixer;
        }
        throw new IllegalArgumentException("no such mixer: " + info);
    }
}
