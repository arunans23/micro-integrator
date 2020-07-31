package org.wso2.micro.integrator.management.apis;

import org.apache.axiom.om.OMOutputFormat;
import org.apache.synapse.MessageContext;
import org.apache.synapse.config.SynapseConfiguration;

import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

public class RemoteCAppUploaderResource implements MiApiResource {
    public RemoteCAppUploaderResource() {
        super();
    }

    @Override
    public Set<String> getMethods() {
        Set<String> methods = new HashSet<>();
        methods.add(Constants.HTTP_POST);
        return methods;
    }

    @Override
    public boolean invoke(MessageContext messageContext, org.apache.axis2.context.MessageContext axis2MessageContext,
                          SynapseConfiguration synapseConfiguration) {
        try {
            byte[] bytes = Base64.getDecoder().decode(axis2MessageContext.getEnvelope().getBody().getFirstElement().getText());
            OMOutputFormat format = new OMOutputFormat();
            MimeMultipart mp = new MimeMultipart(new ByteArrayDataSource(bytes, format.getContentType()));
            BodyPart bp = mp.getBodyPart(0);
            String fileName = bp.getHeader("Content-Disposition")[0].split(";")[2].split("=")[1];
            fileName = fileName.substring(1, fileName.length()-1);
            String fileNamePath = System.getProperty("carbon.home") + File.separator + "repository" + File.separator + "deployment"
                    + File.separator + "server" + File.separator + "carbonapps" + File.separator + fileName;
            Path path = Paths.get(fileNamePath);
            Files.write(path, bytes);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        } catch (MessagingException e) {
            System.out.println(e.getMessage());
        }
        return false;
    }
}
