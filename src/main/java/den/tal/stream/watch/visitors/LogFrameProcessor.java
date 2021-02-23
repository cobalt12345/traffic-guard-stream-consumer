package den.tal.stream.watch.visitors;

import com.amazonaws.kinesisvideo.parser.mkv.Frame;
import com.amazonaws.kinesisvideo.parser.mkv.FrameProcessException;
import com.amazonaws.kinesisvideo.parser.utilities.FragmentMetadata;
import com.amazonaws.kinesisvideo.parser.utilities.FrameVisitor;
import com.amazonaws.kinesisvideo.parser.utilities.MkvTrackMetadata;
import lombok.extern.log4j.Log4j2;

import java.util.Optional;

@Log4j2
public class LogFrameProcessor implements FrameVisitor.FrameProcessor {

    @Override
    public void process(Frame frame, MkvTrackMetadata trackMetadata, Optional<FragmentMetadata> fragmentMetadata) throws FrameProcessException {
        log.debug("Frame: {} Track metadata: {} Fragment metadata: {} ", frame, trackMetadata,
                fragmentMetadata.orElse(null));
    }
}
