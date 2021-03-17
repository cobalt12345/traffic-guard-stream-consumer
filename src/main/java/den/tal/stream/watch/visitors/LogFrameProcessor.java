package den.tal.stream.watch.visitors;

import com.amazonaws.kinesisvideo.parser.mkv.Frame;
import com.amazonaws.kinesisvideo.parser.mkv.FrameProcessException;
import com.amazonaws.kinesisvideo.parser.utilities.FragmentMetadata;
import com.amazonaws.kinesisvideo.parser.utilities.FrameVisitor;
import com.amazonaws.kinesisvideo.parser.utilities.MkvTrackMetadata;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class LogFrameProcessor implements FrameVisitor.FrameProcessor {

    @Override
    public void process(Frame frame, MkvTrackMetadata trackMetadata, Optional<FragmentMetadata> fragmentMetadata)
            throws FrameProcessException {

        log.debug("Frame: {} Track metadata: {} Fragment metadata: {} ", frame, trackMetadata,
                fragmentMetadata.orElse(null));
    }
}
