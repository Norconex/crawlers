FROM ubuntu:22.04
ARG committer_type

WORKDIR /nxer

RUN if [ "$committer_type" = "es" ]; then \
      #COPY $committer_type /nxer && \
	    mkdir /nxer/es_folder && \
      echo "es copy"; \
	  elif [ "$committer_type" = "solr" ]; then \
      echo "solr copy" && \
	    mkdir /nxer/solr-folder; \
	  else \
      echo "not to copy" && \
	    mkdir /nxer/empty-copy; \
	  fi
 
#COPY crawler/web/target/*.zip /nxer/
