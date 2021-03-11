package den.tal.stream;

import den.tal.stream.watch.FilmWatcher;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Iterator;

@SpringBootApplication()
@Log4j2
public class StreamConsumerStart {

    public static void main(String[] args) {
        var applicationContext = SpringApplication.run(StreamConsumerStart.class, args);

        if (log.isDebugEnabled()) {
            for (Iterator<String> namesIter = applicationContext.getBeanFactory().getBeanNamesIterator();
                 namesIter.hasNext(); ) {

                log.debug("Bean: {}", namesIter.next());
            }
        }
        applicationContext.getBean(FilmWatcher.class).beginWatchFilm();
    }
}
