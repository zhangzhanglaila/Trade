package cait.collector;

import com.github.yitter.contract.IdGeneratorOptions;
import com.github.yitter.idgen.YitIdHelper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CollectorMain {

	public static void main(String[] args) {
		SpringApplication.run(CollectorMain.class, args);
        IdGeneratorOptions options = new IdGeneratorOptions((short) 0);
        options.WorkerIdBitLength = 2;
        options.SeqBitLength = 4;
        YitIdHelper.setIdGenerator(options);
	}

}
