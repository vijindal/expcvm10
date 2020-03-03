/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package phase;

import java.util.ArrayList;

/**
 *
 * @author admin
 */
public class A2TTERN extends CECVM {

    private final int tcdis_local = 5;
    private final int nxcdis_local = 1;
    private final int ncdis_local = 4;
    private final double[] mhdis_local = {6, 12, 3, 4, 1};
    private final int rcdis_local[] = {4, 3, 2, 2, 1, 0}; //  No of sites for each cluster
    private final int nij[][]
            = {{1, 4, 2, 4, 4, 1},
            {0, 1, 1, 2, 3, 1},
            {0, 0, 1, 0, 2, 1},
            {0, 0, 0, 1, 2, 1},
            {0, 0, 0, 0, 1, 1},
            {0, 0, 0, 0, 0, 1}};

    //Phase specific information
    private final int n_local = 1;//default value of number of moles
    private final int numComp = 3;
    //cluster information
    private final String phaseTag_local = "A2TTERN";
    private final int lc_local[] = {1, 1, 1, 1, 1};
    private final int tc_local = 5;
    private final int nxc_local = 1;
    private final int nc_local = 4;
    private final int rc_local[][][] = {{{4}}, {{3}}, {{2}}, {{2}}, {{1}}, {{0}}};
    private final int mh_local[][] = {{1}, {1}, {1}, {1}, {1}, {1}};
    //CF information
    private final int tcfdis_local = 20;
    private final int[] mcfdis_local = {6, 24, 12, 24, 24, 6, 12, 12, 24, 24, 12, 12, 3, 6, 3, 4, 8, 4, 1, 1};
    private final int[][] rcfdis_local = {{4}, {4}, {4}, {4}, {4}, {4}, {3}, {3}, {3}, {3}, {3}, {3}, {2}, {2}, {2}, {2}, {2}, {2}, {1}, {1}, {0}};
    private final int lcf_local[][] = {{1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1}, {1, 1, 1}, {1, 1, 1}, {1, 1}, {1}};
    private final int tcf_local = 21;
    private final int nxcf_local = 3;
    private final int ncf_local = 18;
    private final int rcf_local[][][][] = {{{{4}}, {{4}}, {{4}}, {{4}}, {{4}}, {{4}}}, {{{3}}, {{3}}, {{3}}, {{3}}, {{3}}, {{3}}}, {{{2}}, {{2}}, {{2}}}, {{{2}}, {{2}}, {{2}}}, {{{1}}, {{1}}}, {{{0}}}};
    private final double mcf_local[][][] = {{{6}, {24}, {12}, {24}, {24}, {6}}, {{12}, {12}, {24}, {24}, {12}, {12}}, {{3}, {6}, {3}}, {{4}, {8}, {4}}, {{1}, {1}}};
    private final int wcv_local[][][] = {{{1, 4, 4, 4, 8, 4, 2, 4, 4, 4, 8, 8, 2, 4, 4, 1, 4, 4, 2, 4, 1}}, {{1, 1, 1, 2, 2, 2, 2, 2, 2, 1, 1, 1, 2, 2, 2, 1, 1, 1}}, {{1, 2, 2, 1, 2, 1}}, {{1, 2, 2, 1, 2, 1}}, {{1, 1, 1}}};
    private final int[][] lcv_local = {{21}, {18}, {6}, {6}, {3}};
    private final double[][][][] cmat_local = {{{{1., 1., 0., 2., 0., 0., 1., 1., 0., 0., 0., 2., 0., 0.,
        0., -2., -2., 0., 1., 0., 0.}, {-1., 0., 0., -1., 0., 0., -0.5,
        0., 0., 0.5, -0.5, -0.5, -0.5, 0., 0., 1., 0., 0., 0., 0.,
        0.}, {0., -1., 0., -1., 0., 0., 0., -0.5, 0., -0.5, 0.5, -0.5,
        0., -0.5, 0., 0., 1., 0., 0., 0., 0.}, {1., 0., 0., 0.5, 0.5, 0.5,
        0., 0., 0., -1.5, 0.5, 0.5, 1., 0., 0., -1., 0., 0., 0., 0.,
        0.}, {0., 0., 0., 0.5, -0.5, -0.5, 0., 0., 0., 0.5, 0.5, -0.5, 0.,
        0., 0., 0., 0., 0., 0., 0., 0.}, {0., 1., 0., 0.5, 0.5, 0.5, 0.,
        0., 0., 0.5, -1.5, 0.5, 0., 1., 0., 0., -1., 0., 0., 0., 0.}, {1.,
        0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0.,
        0., 0., 0., 0.}, {-1., 0., 0., 0., -1., 0., 0.5, 0., 0.,
        0.5, -0.5, -0.5, -0.5, 0., 0., 1., 0., 0., 0., 0., 0.}, {0., 0.,
        0., 0., 1., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0.,
        0., 0., 0.}, {0., 0., 0., 1., 0., 0., 0., 0., 0., 0., 0., 0., 0.,
        0., 0., 0., 0., 0., 0., 0., 0.}, {0., 0., 0., -0.5, 0.5, -0.5, 0.,
        0., 0., 0.5, -0.5, 0.5, 0., 0., 0., 0., 0., 0., 0., 0., 0.}, {0.,
        0., 0., -0.5, -0.5, 0.5, 0., 0., 0., -0.5, 0.5, 0.5, 0., 0., 0.,
        0., 0., 0., 0., 0., 0.}, {0., 1., 0., 0., 0., 0., 0., 0., 0., 0.,
        0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0.}, {0., 0., 0., 0., 0.,
        1., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0.,
        0.}, {0., -1., 0., 0., 0., -1., 0., 0.5, 0., -0.5, 0.5, -0.5,
        0., -0.5, 0., 0., 1., 0., 0., 0., 0.}, {1., 0., 1., 0., 2.,
        0., -1., 0., 1., 0., 2., 0., 0., 0., 0., -2., 0., -2., 0., 1.,
        0.}, {0., 0., -1., 0., -1., 0., 0., 0., -0.5, -0.5, -0.5, 0.5, 0.,
        0., -0.5, 0., 0., 1., 0., 0., 0.}, {0., 0., 1., 0.5, 0.5, 0.5,
        0., 0., 0., 0.5, 0.5, -1.5, 0., 0., 1., 0., 0., -1., 0., 0.,
        0.}, {0., 0., 1., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0.,
        0., 0., 0., 0., 0., 0.}, {0., 0., -1., 0., 0., -1., 0., 0.,
        0.5, -0.5, -0.5, 0.5, 0., 0., -0.5, 0., 0., 1., 0., 0., 0.}, {0.,
        1., 1., 0., 0., 2., 0., -1., -1., 2., 0., 0., 0., 0., 0., 0., -2.,
        -2., 0., 0., 1.}}}, {{{0., 0., 0., 0., 0., 0., 0.5, 0.5, 0., 0.,
        0., 1., -0.5, -0.5, 0., -1., -1., 0., 1., 0., 0.}, {0., 0., 0.,
        0., 0., 0., -0.5, 0., 0., 0.5, -0.5, -0.5, -0.5, 0., 0., 1., 0.,
        0., 0., 0., 0.}, {0., 0., 0., 0., 0., 0., 0., -0.5, 0., -0.5,
        0.5, -0.5, 0., -0.5, 0., 0., 1., 0., 0., 0., 0.}, {0., 0., 0., 0.,
        0., 0., -0.5, 0., 0., -0.5, 0.5, -0.5, 0.5, 0., 0., 0., 0., 0.,
        0., 0., 0.}, {0., 0., 0., 0., 0., 0., 0.5, 0., 0., -0.5, -0.5,
        0.5, 0.5, 0., 0., 0., 0., 0., 0., 0., 0.}, {0., 0., 0., 0., 0.,
        0., 0., 0., 0., 1., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0.,
        0.}, {0., 0., 0., 0., 0., 0., 0., -0.5, 0., 0.5, -0.5, -0.5, 0.,
        0.5, 0., 0., 0., 0., 0., 0., 0.}, {0., 0., 0., 0., 0., 0., 0., 0.,
        0., 0., 1., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0.}, {0., 0., 0.,
        0., 0., 0., 0., 0.5, 0., -0.5, -0.5, 0.5, 0., 0.5, 0., 0., 0.,
        0., 0., 0., 0.}, {0., 0., 0., 0., 0., 0., 0.5, 0., 0.,
        0.5, -0.5, -0.5, -0.5, 0., 0., 1., 0., 0., 0., 0., 0.}, {0., 0.,
        0., 0., 0., 0., -0.5, 0., 0.5, 0., 1., 0., -0.5, 0., -0.5, -1.,
        0., -1., 0., 1., 0.}, {0., 0., 0., 0., 0., 0., 0.,
        0., -0.5, -0.5, -0.5, 0.5, 0., 0., -0.5, 0., 0., 1., 0., 0.,
        0.}, {0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 1., 0., 0., 0.,
        0., 0., 0., 0., 0., 0.}, {0., 0., 0., 0., 0., 0., 0., 0., -0.5,
        0.5, -0.5, -0.5, 0., 0., 0.5, 0., 0., 0., 0., 0., 0.}, {0., 0.,
        0., 0., 0., 0., 0., 0., 0.5, -0.5, 0.5, -0.5, 0., 0., 0.5, 0., 0.,
        0., 0., 0., 0.}, {0., 0., 0., 0., 0., 0., 0., 0.5, 0., -0.5,
        0.5, -0.5, 0., -0.5, 0., 0., 1., 0., 0., 0., 0.}, {0., 0., 0., 0.,
        0., 0., 0., 0., 0.5, -0.5, -0.5, 0.5, 0., 0., -0.5, 0., 0., 1.,
        0., 0., 0.}, {0., 0., 0., 0., 0., 0., 0., -0.5, -0.5, 1., 0., 0.,
        0., -0.5, -0.5, 0., -1., -1., 0., 0., 1.}}}, {{{0., 0., 0., 0.,
        0., 0., 0., 0., 0., 0., 0., 0., -1., -1., 0., 0., 0., 0., 1., 0.,
        0.}, {0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 1., 0., 0.,
        0., 0., 0., 0., 0., 0.}, {0., 0., 0., 0., 0., 0., 0., 0., 0., 0.,
        0., 0., 0., 1., 0., 0., 0., 0., 0., 0., 0.}, {0., 0., 0., 0., 0.,
        0., 0., 0., 0., 0., 0., 0., -1., 0., -1., 0., 0., 0., 0., 1.,
        0.}, {0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 1.,
        0., 0., 0., 0., 0., 0.}, {0., 0., 0., 0., 0., 0., 0., 0., 0., 0.,
        0., 0., 0., -1., -1., 0., 0., 0., 0., 0., 1.}}}, {{{0., 0., 0.,
        0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., -1., -1., 0., 1.,
        0., 0.}, {0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0.,
        0., 1., 0., 0., 0., 0., 0.}, {0., 0., 0., 0., 0., 0., 0., 0., 0.,
        0., 0., 0., 0., 0., 0., 0., 1., 0., 0., 0., 0.}, {0., 0., 0., 0.,
        0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., -1., 0., -1., 0., 1.,
        0.}, {0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0.,
        0., 0., 1., 0., 0., 0.}, {0., 0., 0., 0., 0., 0., 0., 0., 0., 0.,
        0., 0., 0., 0., 0., 0., -1., -1., 0., 0., 1.}}}, {{{0., 0., 0.,
        0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 1.,
        0., 0.}, {0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0.,
        0., 0., 0., 0., 0., 1., 0.}, {0., 0., 0., 0., 0., 0., 0., 0., 0.,
        0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 0., 1.}}}};

    public A2TTERN() {//Constructor Method to access getInitlIntVarValues method
    }

    public A2TTERN(String stdst[], double ecdis[], double evdis[], double T_in, ArrayList<Double> x) { // Constructor Method
        //Print.f("*****A2mTBINc constructor called", 6);
        //setEmat(eMatFile);//vj-15-03-11

        // Phase specific information
        setTcdis(tcdis_local); //  No of total clusters
        setNxcdis(nxcdis_local); //  No of clusters realted to point cluters
        setNcdis(ncdis_local);
        setMhdis(mhdis_local);
        setRcdis(rcdis_local);
        //setUab(tcdis_local, rcdis_local);//Calculate and set uA and uB arrays
        setNijTable(nij);
        setLc(lc_local);//List of clusters in case of broken symemtry(Ordered phases)per highest symmetry(disordred phase)cluster
        setTc(tc_local);
        setNxc(nxc_local);
        setNc(nc_local);
        setRc(rc_local);
        setMh(mh_local);
        setTcfdis(tcfdis_local);
        setMcfdis(mcfdis_local);
        setRcfdis(rcfdis_local);
        setLcf(lcf_local);
        setTcf(tcf_local);
        setNxcf(nxcf_local);
        setNcf(ncf_local);
        setRcf(rcf_local);
        setMcf(mcf_local);
        setWcv(wcv_local);
        setLcv(lcv_local);
        setCMat(cmat_local);//vj-15-03-12 
// Parameters
        setPhaseTag(phaseTag_local);
        setN(n_local);
        setNumComp(this.numComp);
        setR(8.3144598); //  Universal gas constant
        setT(T_in);
        //setX(getInitlIntVarValues(x));
        setX(x);
        setPhaseParam();
        setEcdis(ecdis);
        setEvdis(evdis);
        setMsdis(mhdis_local);
        updateCV();
        calG0List();
        calG0TList();
        calG0PList();
        //setNP(np_local);
        //setReferenceStateParameters(stdst);
        //updateStdst();
        //Microscopic Parameters
        //genInitialValues(x);//set u and update cluster variables as well
        // Initialize Arrays with phase specific size
        //initScu(Scu_local);
        //initHcu(Hcu_local);
        //initGcu(Gcu_local);
        //initScuu(Scuu_local);
        //initGcuu(Gcuu_local);
        //Print.f("****A2mTBINc constructor ended", 6);
    }

    @Override
    public ArrayList<Double> getInitlIntVarValues(ArrayList<Double> x) {//Phase specific method
        ArrayList<Double> x_out = new ArrayList<>();
        double XA = x.get(0);
        double XB = x.get(1);
        double XC = x.get(2);
        x_out.add(XA * XA * XB * XB);
        x_out.add(XA * XA * XC * XC);
        x_out.add(XB * XB * XC * XC);
        x_out.add(XA * XA * XB * XC);
        x_out.add(XA * XB * XB * XC);
        x_out.add(XA * XB * XC * XC);
        x_out.add(-XA * XA * XB + XA * XB * XB);
        x_out.add(-XA * XA * XC + XA * XC * XC);
        x_out.add(-XB * XB * XC + XB * XC * XC);
        x_out.add(XA * XB * XC);
        x_out.add(XA * XB * XC);
        x_out.add(XA * XB * XC);
        x_out.add(XA * XB);
        x_out.add(XA * XC);
        x_out.add(XB * XC);
        x_out.add(XA * XB);
        x_out.add(XA * XC);
        x_out.add(XB * XC);
        x_out.add(XA);
        x_out.add(XB);
        x_out.add(XC);
        //setU(x_out);
        return (x_out);
    }
    
}
