package calbince;

import utils.io.Print;
import utils.jama.GaussjNR;
import utils.jama.Mat;
import java.io.IOException;
/*
 * 22.10.11 (SU) : New class for mrqmin
 *
 * @author Shivam
 */

public class OptMrq {

    private static final int NDONE = 4;
    private int ITMAX = 40;
    int ndat, ma, mfit;
    private final double[] x, y, sig, wgt;
    private final ExptDatum[] expdat;
    private final double TOL = 1.e-6;
    double tol;
    private  boolean[] ia;
    private  double[] a;
    private PhaseData phasedata;
    private double[][] covar;
    private double[][] alpha;
    private double chisq;
    private final String cname = "OptMrq";

    public OptMrq(ExptData exptdata, PhaseData phasedata) throws IOException {//vj-2013-03-24-added
        Print.f("", 4);
        Print.f(cname + ".constructor method called {", 4);
        this.ndat = exptdata.getNdat();
        expdat = new ExptDatum[ndat];//vj-2013-04-06-Initiate expdat array
        for (int idat = 0; idat < ndat; idat++) {//vj-2013-04-06-fill expdat array
            expdat[idat] = exptdata.getExptDatum(idat);
        }
        this.x = exptdata.getX();
        this.y = exptdata.getY();
        this.sig = exptdata.getSig();
        this.wgt = exptdata.getWt();
        this.tol = TOL;
        this.ma = phasedata.getNpt();
        this.ia = new boolean[ma];
        this.alpha = new double[ma][ma];
        this.phasedata = phasedata;
        this.a = new double[ma];
        phasedata.getA(a);
        phasedata.getIa(ia);
        covar = new double[ma][ma];
        Print.f(cname + ".constructor method ended }", 4);
    }

    void hold(int i, double val) {
        ia[i] = false;
        a[i] = val;
    }

    void free(int i) {
        ia[i] = true;
    }

    public void fit(int imax_In) throws IOException {
        Print.f(cname + ".fit() method called {", 5);
        this.ITMAX = imax_In;
        phasedata.initStrOpt(ITMAX);//2013-04-06-Initiate output string array
        int j, k, l, iter, done = 0;
        double alamda = 1, ochisq;
        double[] atry = new double[ma];
        double[] beta = new double[ma];
        double[] da = new double[ma];
        double[] da1 = new double[ma];//vj-2012-04-21
        mfit = 0;//Total no of active optimization paramaters
        for (j = 0; j < ma; j++) {
            if (ia[j]) {
                mfit++;
            }
        }
        double[][] oneda = new double[mfit][1];//Dimensions of oneda is mfitX1
        double[][] temp = new double[mfit][mfit];//Dimensions of temp is mfitXmfit
        Print.f("ECI", a, 1);
        Print.f("ma", ma,1);
        Print.f("mfit", mfit,1);

        mrqcof(a, alpha, beta);//updates alfa, beta and chisq for initial values of a
        for (j = 0; j < ma; j++) {
            atry[j] = a[j];//atry updated with the initial values of a
        }
        ochisq = chisq;
        Print.f("Beta", beta, 1);
        //Print.f("alpha", alpha, 1);

        Print.f("Initial Chisq:" + chisq + ", Lambda:" + alamda, 1);
        phasedata.setStrOpt(chisq, alamda, a, beta, da, mfit, 0);

        for (iter = 0; iter < ITMAX; iter++) {
            Print.f("--------------------------------------------", 1);
            Print.f("Iteration: " + (iter + 1), 1);
            if (done == NDONE) {
                alamda = 0.;
            }
            for (j = 0; j < mfit; j++) {
                for (k = 0; k < mfit; k++) {
                    covar[j][k] = alpha[j][k];//covar updated with alfa
                }
                covar[j][j] = alpha[j][j] * (1.0 + alamda);
                for (k = 0; k < mfit; k++) {
                    temp[j][k] = covar[j][k];//temp is updated with covar
                }
                oneda[j][0] = beta[j];//oneda is updated with beta
            }
            //Print.f("temp", temp,1);
            //Print.f("oneda", oneda,1);
            GaussjNR gaussjo = new GaussjNR(temp, oneda);
            gaussjo.solve();
            //oneda = solve(temp, oneda);//temp is LUdecomposed and oneda is updated with solution of (alfa, beta)
            for (j = 0; j < mfit; j++) {
                for (k = 0; k < mfit; k++) {
                    covar[j][k] = temp[j][k];//covar is updated with temp(LUdecomposed covar)
                }
                da[j] = oneda[j][0];//da is updated with oneda(solution of (alfa, beta))
                da1[j] = oneda[j][0];//da1 is updated with oneda(solution of (alfa, beta))
            }
            if (done == NDONE) {
                covsrt(covar);
                covsrt(alpha);
                int dof = (ndat - mfit);
                double factor = Math.sqrt(chisq / dof);
                //System.out.println(dof + ", " + factor);
                for (int i = 0; i < ma; i++) {
                    if (ia[i]) {
                        double sd = Math.sqrt(covar[i][i]);
                        double rsd = Math.abs(sd / a[i]);
                        //System.out.println("sd:" + sd + ", rsd:" + rsd + ", factor:" + factor);
                        Print.f("factor * rsd", factor * rsd, 1);
                    }
                }
                phasedata.setMaxItrFit(iter);
                Print.f("fitmrq:fit() NDONE Reached.", 1);
                return;
            }
            for (j = 0, l = 0; l < ma; l++) {
                if (ia[l]) {
                    atry[l] = a[l] + da[j++];//atry updated with a+da, Dimesnsion of da is increased to match with atry and a 
                }
            }
            Print.f("Trial da", da, 1);
            Print.f("Trial a", atry, 1);
            mrqcof(atry, covar, da);//updates alfa,da and chisq for atry
            //Prnt.f((chisq - ochisq), "(chisq - ochisq)", 0);
            //Prnt.f(tol + "," + tol * chisq, "tol+\",\"+tol * chisq", 0);
            if (Math.abs(chisq - ochisq) < Math.max(tol, tol * chisq)) {
                done++;
            }
            if (chisq < ochisq) {
                Print.f("Success", 1);
                //alamda *= 0.5;
                alamda *= 0.5;
                ochisq = chisq;
                for (j = 0; j < mfit; j++) {
                    for (k = 0; k < mfit; k++) {
                        alpha[j][k] = covar[j][k];//alfa is updated with covar
                    }
                    beta[j] = da[j];//beta is updated with da
                }
                for (l = 0; l < ma; l++) {
                    a[l] = atry[l];//a is updated with atry
                    phasedata.setA(a[l], l);//global a is updated with a
                }

            } else {
                Print.f("Failure", 1);
                alamda *= 2.0;
                chisq = ochisq;
            }
            Print.f("a", a, 1);
            Print.f("beta", beta, 1);
            Print.f("Chisq:" + chisq + ", Lambda:" + alamda, 1);
            Print.f("", 1);
            phasedata.setStrOpt(chisq, alamda, a, beta, da1, mfit, iter + 1);
        }
        Print.f("fitmrq:fit() ITMAX Reached !", 1);
        phasedata.setMaxItrFit(iter);
    }// Closed Method fit()

    void mrqcof(double[] a, double[][] alpha, double[] beta) throws IOException {
        Print.f(cname + ".mrqcof method called{", 5);
        int i, j, k, l, m;
        double[] ymod = {0.0};//vj-2013-03-31:modified
        double wt, sig2i, dy;//vj-2012-03-26:ymod array length changed to 4 from 1
        double[] dyda = new double[ma];
        for (j = 0; j < mfit; j++) {
            for (k = 0; k <= j; k++) {
                alpha[j][k] = 0.0;
            }
            beta[j] = 0.;
        }
        chisq = 0.;
        for (i = 0; i < ndat; i++) {
            //Print.f("...........................................................", 1);
            //Print.f("ndat",i, 1);
            //expdat[i].print();
            funcs(expdat[i], a, ymod, dyda);
            //Print.f("mrqcof.dyda", dyda, 0);
            expdat[i].print(1);
            sig2i = 1.0 / (sig[i] * sig[i]);
            dy = (y[i] - ymod[0]);
            //Prnt.f("ndat:" + datum.getdIndex() + "   Dtyp: " + datum.getBList() + "\t" + "  yExp: " + df.format(y[i]) + "\t" + "  yMod: " + df.format(ymod[0]) + "\t" + "   dy: " + df.format(dy) + "\t" + "x:" + df.format(datum.getX()) + "\t" + "x1:" + df.format(datum.getX1()), 1);
            //Globals.setGlobalOutStrMod(ymod[0], ymod[1], ymod[2], ymod[3], i);
            dy = dy * wgt[i];//2012-03-02(VJ):Modified to include data wt.//2012-03-222(vj):modified
            for (j = 0, l = 0; l < ma; l++) {
                if (ia[l]) {
                    //dy = dy * Globals.wt[i];//2012-03-02(VJ):Modified to include data wt.//2012-03-222(vj):modified
                    dyda[l] = dyda[l] * wgt[i];//2012-03-02(VJ):Modified to include data wt.
                    wt = dyda[l] * sig2i;
                    for (k = 0, m = 0; m < l + 1; m++) {
                        if (ia[m]) {
                            alpha[j][k++] += wt * dyda[m];
                        }
                    }
                    beta[j++] += dy * wt;
                }
            }
            chisq += dy * dy * sig2i;
        }
        for (j = 1; j < mfit; j++) {
            for (k = 0; k < j; k++) {
                alpha[k][j] = alpha[j][k];
            }
        }
        Print.f(cname + ".mrqcof method ended }", 5);
    }// Closed Method OptMrq()

    void covsrt(double[][] covar) {
        int i, j, k;
        for (i = mfit; i < ma; i++) {
            for (j = 0; j < i + 1; j++) {
                covar[i][j] = covar[j][i] = 0.0;
            }
        }
        k = mfit - 1;
        double swap = 0;
        for (j = ma - 1; j > 1; j--) {
            if (ia[j]) {
                for (i = 0; i < ma; i++) {
                    swap = covar[i][k];
                    covar[i][k] = covar[i][j];
                    covar[i][j] = swap;
                }
                for (i = 0; i < ma; i++) {
                    swap = covar[k][i];
                    covar[k][i] = covar[j][i];
                    covar[j][i] = swap;
                }
                k--;
            }
        }
    }

    private void funcs(ExptDatum datum, double[] a, double[] ymod, double[] dyda) throws IOException {
        Print.f(cname + ".funcs method called {", 5);
        //datum.print();
        double[] dyda_local = new double[ma];
        double[] dyda_trans = new double[ma];
        double[][] transMat = new double[ma][ma];
        phasedata.calTransMat(transMat);//VJ-170215
        double[] aTrans = Mat.mul(transMat, a);//for storing transformed a
        phasedata.setA(aTrans);//updating a of phasedata
        //Print.f("transMat", transMat,1);
        //Print.f("aTrans", aTrans,1);
        //phasedata.print();
        Methods methods = new Methods(phasedata);//vj-2013-04-06
        methods.funcsOpt(datum, dyda_trans);
        //Print.f("dyda_trans", dyda_trans,1);
        Mat.LDsolve(transMat, dyda_trans, dyda_local);//vj-2015-11-19: changing to dyda_local=inverse(transMat).dyda_trans
        System.arraycopy(dyda_local, 0, dyda, 0, ma);
        ymod[0] = datum.getYmod();
        Print.f(cname + ".funcs method ended }", 5);
    }


    public double[][] getAlpha() {
        return alpha;
    }

    public double getChisq() {
        return chisq;
    }

    public double[] getParms() {
        return a;
    }


    public void print() throws IOException {//vj-2013-04009
        Print.f(this.getClass().getName() + ".print() method called", 5);
        Print.f("NDONE", NDONE, 0);
        Print.f("ITMAX", ITMAX, 0);
        Print.f("ndat", ndat, 0);
        Print.f("ma", ma, 0);
        Print.f("mfit", mfit, 0);
        Print.f("x", x, 0);
        Print.f("y", y, 0);
        Print.f("sig", sig, 0);
        Print.f("wgt", wgt, 0);
        Print.f("tol", tol, 0);
        Print.f("ia", ia, 0);
        Print.f("a", a, 0);
        Print.f("covar", covar, 0);
        Print.f("alpha", alpha, 0);
        Print.f("chisq", chisq, 0);
        Print.f("TOL", TOL, 0);
        Print.f(this.getClass().getName() + ".print() method ended", 5);
    }
}
