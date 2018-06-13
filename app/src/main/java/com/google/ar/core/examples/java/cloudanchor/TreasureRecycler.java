package com.google.ar.core.examples.java.cloudanchor;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.MotionEventCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStructure;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TreasureRecycler extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final List<Treasure> mItems = new ArrayList<>();
    private Context context;
    private LayoutInflater inflater;
    private OnTreasureRecyclerRequest mCallback;
    private Bitmap treasureBitmap;
    private Bitmap letterBitmap;
    private int selectedBackground;
    private int notSelectedBackground;
    private int selectedTreasurePosition = 0;
    public interface OnTreasureRecyclerRequest{
        void onMapsClicked(int treasureIndex);

        void onHintClicked(int treasureIndex);

        void onPictureHintClicked(int treasureIndex);

        void onTreasureClicked(int treasureIndex);
    }
    public TreasureRecycler(Context context, List<Treasure> items) {
        this.context = context;
        for (Treasure treasure : items) {
            if (treasure.isTrackingThisTreasure()) {
                selectedTreasurePosition = items.indexOf(treasure);
            }
        }
        mItems.addAll(items);
        inflater = LayoutInflater.from(context);
        Activity activity= null;
        if (context instanceof HuntTreasureActivity){
            activity = (HuntTreasureActivity) context;
        }
        try {

            mCallback = (OnTreasureRecyclerRequest) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement onRecyclerWorkoutExerciseRequest");
        }
        letterBitmap = BitmapFactory.decodeResource(context.getResources(),R.drawable.letter);
        treasureBitmap = BitmapFactory.decodeResource(context.getResources(),R.drawable.treasure_chest);
        selectedBackground = ContextCompat.getColor(context, R.color.colorPrimary);
        notSelectedBackground = ContextCompat.getColor(context, R.color.white);
    }

    public void changeTreasureTracked(int newPositionToTrack) {
        if (newPositionToTrack == selectedTreasurePosition) {
            return;
        }
        mItems.get(newPositionToTrack).setTrackingThisTreasure(true);
        mItems.get(selectedTreasurePosition).setTrackingThisTreasure(false);
        notifyItemChanged(newPositionToTrack);
        notifyItemChanged(selectedTreasurePosition);
        selectedTreasurePosition = newPositionToTrack;
    }

    public void addItem(Treasure treasure) {
        mItems.add(treasure);
        notifyItemInserted(mItems.size()-1);
    }
    public Treasure getTreasureAtIndex(int index) {
        return mItems.get(index);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View treasureView = inflater.inflate(R.layout.treasure_type_listview_item, parent, false);
        treasureView.setClipToOutline(true);
        TreasureHolder treasureHolder = new TreasureHolder(treasureView, new TreasureHolder.OnTreasureClicked() {

            @Override
            public void onTreasureClick(View v, int position) {
                mCallback.onTreasureClicked(position);
                changeTreasureTracked(position);

            }

            @Override
            public void onMapsClick(View v, int position) {
                mCallback.onMapsClicked(position);

            }

            @Override
            public void onHintClick(View v, int position) {
                mCallback.onHintClicked(position);

            }

            @Override
            public void onPictureHintClick(View v, int position) {
                mCallback.onPictureHintClicked(position);
            }
        });
        return treasureHolder;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        Treasure treasure = mItems.get(position);
        //todo calculate expire time, should probably be a method in the treasure class
        ((TreasureHolder) holder).expiresAtTextview.setText(treasure.getExpiration());
        if (treasure.getTreasureType() == CreateTreasureActivity.TreasureType.LETTER) {
            ((TreasureHolder) holder).treasureTypeImage.setImageBitmap(letterBitmap);
        }
        else{
            ((TreasureHolder) holder).treasureTypeImage.setImageBitmap(treasureBitmap);
        }
        //todo calculate distance, should probably be a method in the treasure class
        ((TreasureHolder) holder).distanceTextView.setText("100m away");
        if (treasure.isTrackingThisTreasure()) {
            ((TreasureHolder) holder).mainLayout.setBackgroundColor(selectedBackground);

        }else{
            ((TreasureHolder) holder).mainLayout.setBackgroundColor(notSelectedBackground);
        }
    }

    public static class TreasureHolder extends RecyclerView.ViewHolder {
        public final TextView expiresAtTextview;
        public final TextView distanceTextView;
        public final ImageView treasureTypeImage;
        public final Button mapsButton;
        public final Button hintButton;
        public final Button hintPictureButton;
        public final RelativeLayout mainLayout;

        public final OnTreasureClicked mListener;

        public interface OnTreasureClicked {
            void onTreasureClick(View v, int position);
            void onMapsClick(View v, int position);
            void onHintClick(View v, int position);
            void onPictureHintClick(View v, int position);

        }

        public TreasureHolder(View itemView, OnTreasureClicked mListener) {
            super(itemView);
            this.mListener = mListener;

            treasureTypeImage = itemView.findViewById(R.id.treasure_icon);

            mainLayout = (RelativeLayout) itemView.findViewById(R.id.mainLayout);
            mainLayout.setOnClickListener((View v) -> mListener.onTreasureClick(v,getAdapterPosition()));

            mapsButton = itemView.findViewById(R.id.mapsButton);
            mapsButton.setOnClickListener((View v) -> mListener.onMapsClick(v,getAdapterPosition()));

            hintButton = itemView.findViewById(R.id.hintButton);
            hintButton.setOnClickListener((View v) -> mListener.onHintClick(v,getAdapterPosition()));

            hintPictureButton = itemView.findViewById(R.id.pictureHintButton);
            hintPictureButton.setOnClickListener((View v) -> mListener.onPictureHintClick(v,getAdapterPosition()));

            expiresAtTextview = itemView.findViewById(R.id.expireTextView);
            distanceTextView = itemView.findViewById(R.id.distanceTextview);
        }
    }

}
