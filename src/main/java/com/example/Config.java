package com.example;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.event.S3EventNotification;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.mortennobel.imagescaling.AdvancedResizeOp;
import com.mortennobel.imagescaling.DimensionConstrain;
import com.mortennobel.imagescaling.ResampleFilters;
import com.mortennobel.imagescaling.ResampleOp;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Function;

@SpringBootApplication
public class Config {

	private static final int RESIZE_WIDTH = 50;
	private static final int RESIZE_HEIGHT = 50;

	@Bean
	public Function<S3EventNotification, String> function(AmazonS3 s3) {
		return event -> {
			S3EventNotification.S3EventNotificationRecord record = event.getRecords().get(0);
			S3Object s3Object = s3.getObject(record.getS3().getBucket().getName(), record.getS3().getObject().getKey());

			try {
				BufferedImage source = ImageIO.read(s3Object.getObjectContent());
				ResampleOp resampleOp = new ResampleOp(DimensionConstrain.createMaxDimension(RESIZE_WIDTH, RESIZE_HEIGHT, true));
				resampleOp.setFilter(ResampleFilters.getLanczos3Filter());
				resampleOp.setUnsharpenMask(AdvancedResizeOp.UnsharpenMask.Normal);
				BufferedImage resized = resampleOp.filter(source, null);
				ByteArrayOutputStream os = new ByteArrayOutputStream();
				ImageIO.write(resized, StringUtils.getFilenameExtension(s3Object.getKey()), os);

				InputStream is = new ByteArrayInputStream(os.toByteArray());
				ObjectMetadata meta = new ObjectMetadata();
				meta.setContentLength(os.size());

				s3.putObject(s3Object.getBucketName() + "-resized", s3Object.getKey(), is, meta);
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}

			return "Ok";
		};
	}

	@Bean
	public AmazonS3 s3() {
		return new AmazonS3Client();
	}

	public static void main(String[] args) {
		SpringApplication.run(Config.class, args);
	}
}
