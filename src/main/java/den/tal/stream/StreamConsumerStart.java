package den.tal.stream;

import den.tal.stream.watch.FilmWatcher;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Iterator;

@Log4j2
public class StreamConsumerStart {

    public static void main(String[] args) {
        var applicationContext = new AnnotationConfigApplicationContext(StreamConsumerConfig.class);

        if (log.isDebugEnabled()) {
            for (Iterator<String> namesIter = applicationContext.getBeanFactory().getBeanNamesIterator();
                 namesIter.hasNext(); ) {

                log.debug("Bean: {}", namesIter.next());
            }
        }
        applicationContext.getBean(FilmWatcher.class).beginWatchFilm();
    }
}
