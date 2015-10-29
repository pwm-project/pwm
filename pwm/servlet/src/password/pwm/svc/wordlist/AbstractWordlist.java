/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package password.pwm.svc.wordlist;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.health.HealthStatus;
import password.pwm.health.HealthTopic;
import password.pwm.http.ContextManager;
import password.pwm.svc.PwmService;
import password.pwm.util.Helper;
import password.pwm.util.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmHashAlgorithm;
import password.pwm.util.secure.SecureEngine;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

abstract class AbstractWordlist implements Wordlist, PwmService {

    static final PwmHashAlgorithm CHECKSUM_HASH_ALG = PwmHashAlgorithm.SHA1;

    protected WordlistConfiguration wordlistConfiguration;

    protected volatile STATUS wlStatus = STATUS.NEW;
    protected LocalDB localDB;

    protected static final PwmLogger LOGGER = PwmLogger.forClass(AbstractWordlist.class);
    protected String DEBUG_LABEL = "Generic Wordlist";

    protected int storedSize = 0;
    protected boolean debugTrace;

    private ErrorInformation lastError;

    private PwmApplication pwmApplication;
    protected Populator populator;



// --------------------------- CONSTRUCTORS ---------------------------

    protected AbstractWordlist() {
    }

    public void init(final PwmApplication pwmApplication) throws PwmException {
        this.pwmApplication = pwmApplication;
        this.localDB = pwmApplication.getLocalDB();
        if (pwmApplication.getConfig().isDevDebugMode()) {
            debugTrace = true;
        }
    }

    protected final void startup(final LocalDB localDB, final WordlistConfiguration wordlistConfiguration) {
        this.wordlistConfiguration = wordlistConfiguration;
        this.localDB = localDB;
        wlStatus = STATUS.OPENING;

        if (localDB == null) {
            final String errorMsg = "LocalDB is not available, " + DEBUG_LABEL + " will remain closed";
            LOGGER.warn(errorMsg);
            lastError = new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE,errorMsg);
            close();
            return;
        }

        try {
            checkPopulation();
        } catch (Exception e) {
            final String errorMsg = "unexpected error while examining wordlist db: " + e.getMessage();
            if ((e instanceof PwmUnrecoverableException) || (e instanceof NullPointerException) || (e instanceof LocalDBException)) {
                LOGGER.warn(errorMsg);
            } else {
                LOGGER.warn(errorMsg,e);
            }
            lastError = new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE,errorMsg);
            populator = null;
            close();
            return;
        }

        //read stored size
        storedSize = readMetadata().getSize();
        wlStatus = STATUS.OPEN;
    }

    String normalizeWord(final String input) {
        if (input == null) {
            return null;
        }

        String word = input.trim();

        if (!wordlistConfiguration.isCaseSensitive()) {
            word = word.toLowerCase();
        }

        return word.length() > 0 ? word : null;
    }

    protected void checkPopulation()
            throws Exception
    {
        final StoredWordlistDataBean storedWordlistDataBean = readMetadata();
        boolean needsBuiltinPopulating = false;
        if (!storedWordlistDataBean.isCompleted()) {
            needsBuiltinPopulating = true;
            LOGGER.debug("wordlist stored in database does not have a completed load status, will load built-in wordlist");
        } else if (storedWordlistDataBean.isBuiltin()) {
            final String builtInWordlistHash = getBuiltInWordlistHash();
            if (!builtInWordlistHash.equals(storedWordlistDataBean.getSha1hash())) {
                LOGGER.debug("wordlist stored in database does not have match checksum with built-in wordlist file, will load built-in wordlist");
                needsBuiltinPopulating = true;
            }
        }

        if (!needsBuiltinPopulating) {
            return;
        }

        this.populateBuiltIn();
    }


    public boolean containsWord(final String word) {
        if (wlStatus != STATUS.OPEN) {
            return false;
        }

        final String testWord = normalizeWord(word);

        if (testWord == null || testWord.length() < 1) {
            return false;
        }


        final Set<String> testWords = chunkWord(testWord, this.wordlistConfiguration.getCheckSize());

        final Date startTime = new Date();
        try {
            boolean result = false;
            for (final String t : testWords) {
                if (!result) { // stop checking once found
                    if (localDB.contains(getWordlistDB(), t)) {
                        result = true;
                    }
                }
            }
            final TimeDuration timeDuration = TimeDuration.fromCurrent(startTime);
            if (timeDuration.isLongerThan(100)) {
                LOGGER.debug("wordlist search time for " + testWords.size() + " wordlist permutations was greater then 100ms: " + timeDuration.asCompactString());
            }
            return result;
        } catch (Exception e) {
            LOGGER.error("database error checking for word: " + e.getMessage());
        }

        return false;
    }

    public int size() {
        if (populator != null) {
            return 0;
        }

        return storedSize;
    }

    public synchronized void close() {
        if (populator != null) {
            try {
                populator.cancel();
                populator = null;
            } catch (PwmUnrecoverableException e) {
                LOGGER.error("wordlist populator failed to exit");
            }
        }

        if (wlStatus != STATUS.CLOSED) {
            LOGGER.debug("closed");
        }

        wlStatus = STATUS.CLOSED;
        localDB = null;
    }

    public STATUS status() {
        return wlStatus;
    }

    public String getDebugStatus() {
        if (wlStatus == STATUS.OPENING && populator != null) {
            return populator.makeStatString();
        } else {
            return wlStatus.toString();
        }
    }

    protected abstract Map<String, String> getWriteTxnForValue(String value);

    protected abstract PwmApplication.AppAttribute getMetaDataAppAttribute();

    protected abstract AppProperty getBuiltInWordlistLocationProperty();

    protected abstract LocalDB.DB getWordlistDB();

    public List<HealthRecord> healthCheck() {
        if (wlStatus == STATUS.OPENING) {

            final HealthRecord healthRecord = new HealthRecord(HealthStatus.CAUTION, HealthTopic.Application, this.DEBUG_LABEL + " is not yet open: " + this.getDebugStatus());
            return Collections.singletonList(healthRecord);
        }

        if (lastError != null) {
            final HealthRecord healthRecord = new HealthRecord(HealthStatus.WARN, HealthTopic.Application, this.DEBUG_LABEL + " error: " + lastError.toDebugStr());
            return Collections.singletonList(healthRecord);
        }
        return null;
    }

    public ServiceInfo serviceInfo()
    {
        if (status() == STATUS.OPEN) {
            return new ServiceInfo(Collections.singletonList(DataStorageMethod.LOCALDB));
        } else {
            return new ServiceInfo(Collections.<DataStorageMethod>emptyList());
        }
    }

    protected Set<String> chunkWord(final String input, final int size) {
        int checkSize = size == 0 || size > input.length() ? input.length() : size;
        final TreeSet<String> testWords = new TreeSet<>();
        while (checkSize <= input.length()) {
            for (int i = 0; i + checkSize <= input.length(); i++) {
                final String loopWord = input.substring(i,i + checkSize);
                testWords.add(loopWord);
            }
            checkSize++;
        }

        return testWords;
    }

    public StoredWordlistDataBean readMetadata() {
        final StoredWordlistDataBean storedValue = pwmApplication.readAppAttribute(getMetaDataAppAttribute(),StoredWordlistDataBean.class);
        if (storedValue != null) {
            return storedValue;
        }
        return new StoredWordlistDataBean();
    }

    void writeMetadata(final StoredWordlistDataBean metadataBean) {
        pwmApplication.writeAppAttribute(getMetaDataAppAttribute(),metadataBean);
    }

    @Override
    public void populate(final InputStream inputStream)
            throws IOException, PwmUnrecoverableException
    {
        try {
            populateImpl(inputStream);
        } finally {
            if (!readMetadata().isCompleted()) {
                LOGGER.debug("beginning population using builtin wordlist in background thread");
                final Thread t = new Thread(new Runnable() {
                    public void run() {
                        try {
                            populateBuiltIn();
                        } catch (Exception e) {
                            LOGGER.warn("unexpected error during builtin wordlist population process: " + e.getMessage(),e);
                        }
                        populator = null;
                    }
                }, Helper.makeThreadName(pwmApplication, WordlistManager.class));
                t.setDaemon(true);
                t.start();
            }
        }
    }

    @Override
    public void populateBuiltIn()
            throws IOException, PwmUnrecoverableException
    {
        populateImpl(getBuiltInWordlist());
        {
            final StoredWordlistDataBean storedWordlistDataBean = readMetadata();
            storedWordlistDataBean.setBuiltin(true);
            writeMetadata(storedWordlistDataBean);
        }
    }

    private void populateImpl(final InputStream inputStream)
            throws IOException, PwmUnrecoverableException
    {
        if (inputStream == null) {
            throw new NullPointerException("input stream can not be null for populateImpl()");
        }

        if (wlStatus == STATUS.CLOSED) {
            return;
        }

        wlStatus = STATUS.OPENING;

        try {
            if (populator != null) {
                populator.cancel();

                final int maxWaitMs = 1000 * 30;
                final Date startWaitTime = new Date();
                while (populator.isRunning() && TimeDuration.fromCurrent(startWaitTime).isShorterThan(maxWaitMs)) {
                    Helper.pause(1000);
                }
                if (populator.isRunning() && TimeDuration.fromCurrent(startWaitTime).isShorterThan(maxWaitMs)) {
                    throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN, "unable to abort populator"));
                }
            }

            { // reset the wordlist metadata
                final StoredWordlistDataBean storedWordlistDataBean = new StoredWordlistDataBean();
                writeMetadata(storedWordlistDataBean);
            }

            populator = new Populator(inputStream, this, pwmApplication);
            populator.populate();
        } catch (Exception e) {
            final ErrorInformation populationError;
            populationError = e instanceof PwmException
                    ? ((PwmException) e).getErrorInformation()
                    : new ErrorInformation(PwmError.ERROR_UNKNOWN, e.getMessage());
            LOGGER.error("error during wordlist population: " + populationError.toDebugStr());
            throw new PwmUnrecoverableException(populationError);
        } finally {
            populator = null;
            inputStream.close();
        }

        wlStatus = STATUS.OPEN;
    }

    protected InputStream getBuiltInWordlist() throws FileNotFoundException, PwmUnrecoverableException {
        final ContextManager contextManager = pwmApplication.getPwmEnvironment().getContextManager();
        if (contextManager != null) {
            final String wordlistFilename = pwmApplication.getConfig().readAppProperty(getBuiltInWordlistLocationProperty());
            return contextManager.getResourceAsStream(wordlistFilename);
        }
        throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE,"unable to locate builtin wordlist file"));
    }

    protected String getBuiltInWordlistHash() throws IOException, PwmUnrecoverableException {

        InputStream inputStream = null;
        try {
            inputStream = getBuiltInWordlist();
            return SecureEngine.hash(inputStream, CHECKSUM_HASH_ALG);
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }

}
